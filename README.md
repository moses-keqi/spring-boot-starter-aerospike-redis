## 改造原因：

> 1、spring-data-aerospike 和 Redis 同时使用导致spring-data-keyvalue冲突<br/>
> 2、支持Redis写法

## 引入方式
  ```xml
  <dependency>
      <groupId>com.moses.aerospike</groupId>
      <artifactId>spring-boot-starter-aerospike-redis</artifactId>
      <version>1.0-SNAPSHOT</version>
  </dependency>
  ```
  
## 配置文件
```properties
spring.aerospike.namespace=test
spring.aerospike.host=127.0.0.1
spring.aerospike.port=3000
```

## 集群配置
```properties
spring.aerospike.namespace=
spring.aerospike.hosts=192.168.1.100:3000,192.168.1.102:3000,192.168.1.102:3000
```

## 引入lua
```properties
spring.aerospike.scriptPath=/user/local/reids.lua #现有代码在resources下载，默认是不要引入，除非自己更好的解决方案
```
