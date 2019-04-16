## 改造原因：

> 1、spring-data-aerospike 和 Redis 同时使用导致spring-data-keyvalue冲突<br/>
> 2、支持Redis写法

## 引入方式
  ```xml
  <dependency>
      <groupId>com.moses.aerospike</groupId>
      <artifactId>spring-boot-starter-aerospike-redis</artifactId>
      <version>1.0.0</version>
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
###### 遇见问题,transaction-pending-limit 配置来提高对同一个key操作的并发量，它的默认为20，值为0时表示不限增大该配置可能会降低一定性能。客户端可能需要对该异常增加重试处理，但重试可能会进一步增大HotKey的风险。 这种基础组件的更迭一定要尽可能使用线上流量做压力检验，从而尽早暴露潜在问题。
```json
transaction-pending-limit 0
```
## 具体配置：
```properties
namespace test {
        replication-factor 2
        memory-size 4G
        default-ttl 30d # 30 days, use 0 to never expire/evict.
        transaction-pending-limit 0
        storage-engine memory
}
```
