/**
 * Project Name:
 * Class Name:com.sms.moses.api.http.config.java
 * <p>
 * Version     Date         Author
 * -----------------------------------------
 * 1.0    2019年4月7日      HanKeQi
 * <p>
 * Copyright (c) 2019, moses All Rights Reserved.
 */
package com.moses.aerospike.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author HanKeQi
 * @Description
 * @date 2019/4/7 1:45 AM
 **/
@ConfigurationProperties(prefix = "spring.aerospike")
public class AedisProperties {

    private String namespace = "test";

    private String host;

    private int port = 3000;

    private String hosts;

    private int timeout = 0;

    private String set;

    private String scriptPath;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getSet() {
        return set;
    }

    public void setSet(String set) {
        this.set = set;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }
}
