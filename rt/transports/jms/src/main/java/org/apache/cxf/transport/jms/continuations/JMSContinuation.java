/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.transport.jms.continuations;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.jms.JMSConfiguration;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

public class JMSContinuation implements Continuation {
    
    private Bus bus;
    private Message inMessage;
    private MessageObserver incomingObserver;
    private Collection<JMSContinuation> continuations;
    private AbstractMessageListenerContainer jmsListener;
    private JMSConfiguration jmsConfig;
    
    private volatile Object userObject;
    
    private volatile boolean isNew = true;
    private volatile boolean isPending;
    private volatile boolean isResumed;
    private Timer timer;
    
    public JMSContinuation(Bus b, Message m, MessageObserver observer,
                           Collection<JMSContinuation> cList, 
                           AbstractMessageListenerContainer jmsListener,
                           JMSConfiguration jmsConfig) {
        bus = b;
        inMessage = m;    
        incomingObserver = observer;
        continuations = cList;
        this.jmsListener = jmsListener;
        this.jmsConfig = jmsConfig;
    }    
    
    public Object getObject() {
        return userObject;
    }

    public boolean isNew() {
        return isNew;
    }

    public boolean isPending() {
        return isPending;
    }

    public boolean isResumed() {
        return isResumed;
    }

    public synchronized void reset() {
        cancelTimerTask();
        isNew = true;
        isPending = false;
        isResumed = false;
        userObject = null;
    }

    public synchronized void resume() {
        if (isResumed || !isPending) {
            return;
        }
        isResumed = true;
        cancelTimerTask();
        doResume();
    }
    
    protected void doResume() {
        updateContinuations(true);
        BusFactory.setThreadDefaultBus(bus);
        try {
            incomingObserver.onMessage(inMessage);
        } finally {
            isPending = false;
            BusFactory.setThreadDefaultBus(null);
        }
    }

    public void setObject(Object o) {
        userObject = o;
    }

    public synchronized boolean suspend(long timeout) {
        
        if (isPending) {
            return false;
        }
        // Need to get the right message which is handled in the interceptor chain
        inMessage.getExchange().getInMessage().getInterceptorChain().suspend();
        updateContinuations(false);
                
        isNew = false;
        isResumed = false;
        isPending = true;
        
        if (timeout > 0) {
            createTimerTask(timeout);
        }
        return true;
    }

    protected void createTimerTask(long timeout) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                synchronized (JMSContinuation.this) { 
                    if (isPending) {
                        cancelTimerTask();
                        doResume();
                    }
                }
            }
        }, timeout);
    }
    
    protected void cancelTimerTask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    
    protected void updateContinuations(boolean remove) {

        if (jmsConfig.getMaxSuspendedContinuations() < 0
            || (jmsListener instanceof DefaultMessageListenerContainer
                && ((DefaultMessageListenerContainer)jmsListener).getCacheLevel() 
                    >= DefaultMessageListenerContainer.CACHE_CONSUMER)) {
            modifyList(remove);
            return;
        }
        
        // throttle the flow if there're too many continuation instances in memory
        synchronized (continuations) {
            modifyList(remove);
            if (continuations.size() >= jmsConfig.getMaxSuspendedContinuations()) {
                jmsListener.stop();
            } else if (!jmsListener.isRunning()) {
                int limit = jmsConfig.getReconnectPercentOfMax();
                if (limit < 0 || limit > 100) {
                    limit = 70;
                }
                limit = (limit * jmsConfig.getMaxSuspendedContinuations()) / 100; 
            
                if (continuations.size() <= limit) {
                    jmsListener.start();
                }
            }
        }

    }
    
    protected void modifyList(boolean remove) {
        if (remove) {
            continuations.remove(this);
        } else {
            continuations.add(this);
        }
    }
    
    
    
}
