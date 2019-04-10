/**
 * Project Name:
 * Class Name:com.moses.aerospike.java
 * <p>
 * Version     Date         Author
 * -----------------------------------------
 * 1.0    2019年04月08日      HanKeQi
 * <p>
 * Copyright (c) 2019, moses All Rights Reserved.
 */
package com.moses.aerospike;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

/**
 * @author HanKeQi
 * @Description
 * @date 2019/4/8 4:37 PM
 **/

public class AedisHolderListener implements ApplicationListener<ApplicationEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AedisHolderListener.class);


    private static Thread holdThread;
    private static Boolean running = Boolean.FALSE;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationPreparedEvent) {
            if (running == Boolean.FALSE)
                running = Boolean.TRUE;
            if (holdThread == null) {
                holdThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace(Thread.currentThread().getName());
                        }
                        while (running && !Thread.currentThread().isInterrupted()) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                }, "Aerospike-Holder");
                holdThread.setDaemon(false);
                holdThread.start();
            }
        }
        if (event instanceof ContextClosedEvent) {
            stopApplicationContext(Boolean.FALSE);
        }
    }

    public static void stopApplicationContext(Boolean stop){
        running = stop.booleanValue();
        if (null != holdThread) {
            holdThread.interrupt();
            holdThread = null;
        }
    }
}
