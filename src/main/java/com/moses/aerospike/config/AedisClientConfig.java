/**
 * Project Name:
 * Class Name:com.moses.aerospike.config.java
 * <p>
 * Version     Date         Author
 * -----------------------------------------
 * 1.0    2019年04月08日      HanKeQi
 * <p>
 * Copyright (c) 2019, moses All Rights Reserved.
 */
package com.moses.aerospike.config;

import com.aerospike.client.Host;
import com.moses.aerospike.properties.AedisProperties;
import com.moses.aerospike.aedis.AedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author HanKeQi
 * @Description
 * @date 2019/4/8 4:14 PM
 **/

@Configuration
@EnableConfigurationProperties(AedisProperties.class)
public class AedisClientConfig {

    @Autowired
    private AedisProperties properties;

    @Bean
    public AedisClient aedisClient() throws Exception{
        if (StringUtils.isEmpty(properties.getHosts())) {
            try {
                return new AedisClient(properties);
            } catch (Exception e) {
                throw new Exception("Please check the configuration.");
            }
        }
        return aedisClientCluster();
    }
    //集群操作
    private AedisClient aedisClientCluster() throws Exception {
        List<Host> list = new ArrayList<>();
        try {
            String[] commaDelimited = StringUtils.commaDelimitedListToStringArray(properties.getHosts());
            for (String str : commaDelimited){
                String[] colonDelimited = StringUtils.delimitedListToStringArray(str, ":");
                list.add(new Host(colonDelimited[0], Integer.valueOf(colonDelimited[1]).intValue()));
            }
            return new AedisClient(list, properties);
        }catch (Exception e){
            throw new Exception("Please check the configuration properties of the configuration cluster.");
        }
    }

}
