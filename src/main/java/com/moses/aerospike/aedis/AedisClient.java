/**
 * Project Name:
 * Class Name:com.moses.aerospike.jedis.java
 * <p>
 * Version     Date         Author
 * -----------------------------------------
 * 1.0    2019年04月08日      HanKeQi
 * <p>
 * Copyright (c) 2019, moses All Rights Reserved.
 */
package com.moses.aerospike.aedis;

/**
 * @author HanKeQi
 * @Description
 * @date 2019/4/9 1:08 PM
 **/

import com.aerospike.client.*;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.*;
import com.moses.aerospike.properties.AedisProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AedisClient {

    private static final String REDIS_LUA_PATH = "redis.lua";

    private AerospikeClient asClient;
    private WritePolicy writePolicy;
    private Policy policy;
    private ScanPolicy scanPolicy;
    private QueryPolicy queryPolicy;
    private String namespace;
    private String redisBin = "redis-bin";
    private String redisSet = null;
    private String keyBin = "redis-key-bin";

    private static final long AS_TIME_OFFSET = 1262304000000L;// in milliseconds

    public enum LIST_POSITION {
        BEFORE, AFTER;
    }

    public AedisClient() {
        super();
        this.writePolicy = new WritePolicy();
        this.writePolicy.recordExistsAction = RecordExistsAction.REPLACE;
        this.policy = new Policy();
        this.scanPolicy = new ScanPolicy();
        this.queryPolicy = new QueryPolicy();

    }

    public AedisClient(Collection<Host> hosts, AedisProperties prop) {
        this();
        this.asClient = new AerospikeClient(null, hosts.toArray(new Host[hosts.size()]));
        this.namespace = prop.getNamespace();
        this.redisSet = prop.getSet();
        checkUdfRegistration(prop.getScriptPath());
        if (prop.getTimeout() > 0){
            setTimeout(prop.getTimeout());
        }
    }


    public AedisClient(AedisProperties prop) {
        this();
        this.asClient = new AerospikeClient(prop.getHost(), prop.getPort());
        this.namespace = prop.getNamespace();
        this.redisSet = prop.getSet();
        checkUdfRegistration(prop.getScriptPath());
        if (prop.getTimeout() > 0){
            setTimeout(prop.getTimeout());
        }
    }

    public void setTimeout(int timeout){
        this.policy.timeout = timeout;
        this.writePolicy.timeout = timeout;
        this.scanPolicy.timeout = timeout;
        this.queryPolicy.timeout = timeout;
    }

    private void checkUdfRegistration(String path){
        info("udf-list");
        if (!StringUtils.isEmpty(path)){
            this.asClient.register(null, path, REDIS_LUA_PATH, Language.LUA);
            return;
        }
        this.asClient.register(null, this.getClass().getClassLoader(), REDIS_LUA_PATH, REDIS_LUA_PATH, Language.LUA);
    }

    private String[] infoAll(AerospikeClient client,
                             String infoString) {
        String[] messages = new String[client.getNodes().length];
        int index = 0;
        for (Node node : client.getNodes()){
            messages[index] = Info.request(node, infoString);
        }
        return messages;
    }

    private String info(String infoString) {
        if (this.asClient != null && this.asClient.isConnected()){
            String answer = Info.request(this.asClient.getNodes()[0], infoString);
            return answer;
        } else {
            return "Client not connected";
        }
    }


    public String set(Object key, Object value){
        return set(null, key, value);
    }

    public String set(WritePolicy wp, Object key, Object value){
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        Bin keyBin = new Bin(this.keyBin , key);
        Bin valueBin = new Bin(this.redisBin, Value.get(value));
        this.asClient.put((wp == null) ? this.writePolicy : wp, asKey, keyBin, valueBin);
        return "OK";

    }

    public String mset(final String... keysvalues) {
        if (keysvalues.length % 2 != 0)
            return "Keys and Values mismatch";
        String key = null;
        boolean isKey = true;
        for (String keyvalue : keysvalues){
            if (isKey) {
                key = keyvalue;
                isKey = false;
            } else {
                set(null, key, Value.get(keyvalue));
                isKey = true;
            }
        }
        return "OK";
    }

    public long msetnx(final String... keysvalues) {
        if (keysvalues.length % 2 != 0)
            return 0L;
        long retVal = 0L;
        String key = null;
        WritePolicy wp = new WritePolicy();
        wp.timeout = this.writePolicy.timeout;
        wp.recordExistsAction = RecordExistsAction.CREATE_ONLY;
        boolean isKey = true;
        try {
            for (String keyvalue : keysvalues){
                if (isKey) {
                    key = keyvalue;
                    isKey = false;
                } else {
                    set(wp, key, Value.get(keyvalue));
                    retVal++;
                    isKey = true;
                }
            }
        } catch (AerospikeException e){
            if (e.getResultCode() != ResultCode.KEY_EXISTS_ERROR)
                throw e;
        }
        return retVal;
    }



    public String setex(Object key, int expiration, Object value) {
        WritePolicy wp = new WritePolicy();
        wp.expiration = expiration;
        set(wp, key, Value.get(value));
        return "OK";
    }



    public String psetex(Object key, int expiration, Object value) {
        return setex(key, expiration/1000, value);
    }

    public boolean setnx(Object key, Object value) {
        try {
            WritePolicy wp = new WritePolicy();
            wp.recordExistsAction = RecordExistsAction.CREATE_ONLY;
            set(wp, key, value);
            return true;
        } catch (AerospikeException e){
            if (e.getResultCode() == ResultCode.KEY_EXISTS_ERROR)
                return false;
            else
                throw e;
        }
    }

    public long setxx(Object key, Object value) {
        try {
            WritePolicy wp = new WritePolicy();
            wp.recordExistsAction = RecordExistsAction.REPLACE_ONLY;
            set(wp, key, value);
            return 1;
        } catch (AerospikeException e){
            if (e.getResultCode() == ResultCode.KEY_NOT_FOUND_ERROR)
                return 0;
            else
                throw e;
        }
    }

    public boolean exists(Object key) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        return this.asClient.exists(this.writePolicy, asKey);
    }

    public long del(Object key) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        this.asClient.delete(writePolicy, asKey);
        return 1;
    }



    public long del(Object ...keys) {
        long count = 0;
        for (Object key : keys){
            Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
            this.asClient.delete(writePolicy, asKey);
            count++;
        }
        return count;
    }

    public Set<String> keys(final String pattern) {
        final Set<String> result = new HashSet<String>();
        this.asClient.scanAll(this.scanPolicy, this.namespace, this.redisSet, (key, record) -> {
            String keyString = (String) record.bins.get(keyBin);
            keysPattern(pattern, keyString, result);
        }, this.keyBin);
        return result;
    }

    public Set<byte[]> keys(byte[] binaryPattern) {
        final String pattern = binaryPattern.toString();
        final Set<byte[]> result = new HashSet<byte[]>();
        this.asClient.scanAll(this.scanPolicy, this.namespace, this.redisSet, (key, record) -> {
            String keyString = (String) record.bins.get(keyBin);
            keysPattern(pattern, keyString, result);
        }, this.keyBin);
        return result;
    }


    public String get(Object key) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        Record record = this.asClient.get(this.policy, asKey, this.redisBin);
        if (record == null) return null;
        String value = (String) record.getValue(this.redisBin);
        return value;
    }


    public List<String> mget(Object ...keys) {
        Key[] asKeys = new Key[keys.length];
        for (int i = 0; i < keys.length; i++){
            asKeys[i] = new Key(this.namespace, this.redisSet, Value.get(keys[i]));
        }
        Record[] records = this.asClient.get(null, asKeys, this.redisBin);
        List<String> result = new ArrayList<String>();
        for (Record record : records){
            result.add((record == null) ? null : (String) record.getValue(this.redisBin));
        }
        return result;
    }


    public void rename(Object oldKey, Object newKey) {
        Key oldAsKey = new Key(this.namespace, this.redisSet, Value.get(oldKey));
        Record record = this.asClient.get(policy, oldAsKey);
        this.set(newKey, (String) record.getValue(this.redisBin));
        this.asClient.delete(this.writePolicy, oldAsKey);
    }

    public long expire(Object key, long expiration) {
        try {
            Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
            WritePolicy wp = new WritePolicy();
            wp.expiration = (int) expiration;
            wp.recordExistsAction = RecordExistsAction.UPDATE_ONLY;
            wp.timeout = this.writePolicy.timeout;
            this.asClient.touch(wp, asKey);
            return 1;
        } catch (AerospikeException e) {
            if (e.getResultCode() == ResultCode.KEY_NOT_FOUND_ERROR){
                return 0;
            } else {
                throw e;
            }
        }
    }

    public long pexpire(Object key, long expiration) {
        return expire(key, expiration / 1000);
    }

    public long expireAt(Object key, long unixTime) {
        try {
            long now = System.currentTimeMillis();
            Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
            WritePolicy wp = new WritePolicy();
            wp.recordExistsAction = RecordExistsAction.UPDATE_ONLY;
            wp.expiration = (int) ((unixTime - now) / 1000);
            this.asClient.touch(wp, asKey);
            return 1;
        } catch (AerospikeException e){
            if (e.getResultCode() == ResultCode.KEY_NOT_FOUND_ERROR){
                return 0;
            } else
                throw e;
        }
    }
    public long pexpireAt(Object key, long unixTime) {
        /*
         * Aerospike only supports expiration units in seconds, not milliseconds
         */
        return expireAt(key, unixTime);
    }

    public long persist(Object key) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        WritePolicy wp = new WritePolicy();
        wp.expiration = -1;
        this.asClient.touch(wp, asKey);
        return 1L;
    }

    public long dbSize() {
        // ns_name=test:set_name=tweets:n_objects=68763:set-stop-write-count=0:set-evict-hwm-count=0:set-enable-xdr=use-default:set-delete=false;
        Pattern pattern = Pattern.compile("ns_name=" + this.namespace + ":set_name=" + this.redisSet + ":n_objects=(\\d+)");
        String[] infoStrings = infoAll(this.asClient, "sets");
        long size = 0;
        for (String info : infoStrings){
            Matcher matcher = pattern.matcher(info);
            while (matcher.find()){
                size += Long.parseLong(matcher.group(1));
            }
        }
        return size;
    }

    public String echo(String message) {
        return message;
    }

    public Long ttl(Object key) {
        try {
            Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
            Record record = this.asClient.getHeader(this.policy, asKey);
            long now = (System.currentTimeMillis() - AS_TIME_OFFSET) / 1000;
            long exp = record.expiration;
            long TTL = (exp - now);
            return TTL;
        } catch (AerospikeException e){
            if (e.getResultCode() == ResultCode.KEY_NOT_FOUND_ERROR){
                return -2L;
            } else {
                throw e;
            }
        }
    }

    public long pttl(String key) {
        return ttl(key) * 1000;
    }

    public String ping() {
        if (this.asClient.isConnected())
            return "PONG";
        else
            return null;
    }

    public long incr(Object key) {
        return incrBy(key, 1);
    }

    public long incrBy(Object key, long increment) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        Bin keyBin = new Bin(this.keyBin , key);
        Bin addBin = new Bin(this.redisBin, Value.get(increment));
        WritePolicy wp = new WritePolicy();
        wp.recordExistsAction = RecordExistsAction.UPDATE;
        Record record = this.asClient.operate(wp, asKey, Operation.put(keyBin), Operation.add(addBin), Operation.get(this.redisBin));
        return record.getInt(this.redisBin);
    }

    public double incrByFloat(Object key, double value) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        Object ret = this.asClient.execute(this.writePolicy, asKey, "redis", "INCRBYFLOAT", Value.get(this.redisBin), Value.get(value));
        return (Double) ret;
    }


    public long decr(Object key) {
        return decrBy(key, 1);
    }


    public long decrBy(Object key, long i) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        Bin keyBin = new Bin(this.keyBin , key);
        Bin addBin = new Bin(this.redisBin, -i);
        WritePolicy wp = new WritePolicy();
        wp.recordExistsAction = RecordExistsAction.UPDATE;
        Record record = this.asClient.operate(wp, asKey, Operation.put(keyBin), Operation.add(addBin), Operation.get(this.redisBin));
        return record.getInt(this.redisBin);
    }


    public Object getSet(Object key, Object value) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        return this.asClient.execute(this.writePolicy, asKey, "redis", "GETSET", Value.get(this.redisBin), Value.get(value));
    }


    public long append(Object key, Object value) {
        Key asKey = new Key(this.namespace, this.redisSet, Value.get(key));
        Bin keyBin = new Bin(this.keyBin , key);
        Bin appendBin = new Bin(this.redisBin, Value.get(value));
        WritePolicy wp = new WritePolicy();
        wp.recordExistsAction = RecordExistsAction.UPDATE;
        Record record = this.asClient.operate(wp, asKey, Operation.put(keyBin), Operation.append(appendBin), Operation.get(this.redisBin));
        return ((String)record.getValue(this.redisBin)).length();
    }

    public String getRange(String key, long startOffset, long endOffset) {
        String result = get(key);
        return result.substring((int) startOffset, (int) endOffset + 1);
    }


    public Object substr(String key, long startOffset, long endOffset) {
        String result = get(key);
        return result.substring((int)startOffset, (int)endOffset+1);
    }


    public Long strlen(String key) {
        String result = get(key);
        return (long) result.length();
    }

    /*
     * List operations
     */
    public long rpush(String key, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Long result =  (Long) this.asClient.execute(this.writePolicy, asKey, "redis", "RPUSH", Value.get(this.redisBin), Value.get(value));
        return result.longValue();
    }


    public long lpush(String key, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Long result =  (Long) this.asClient.execute(this.writePolicy, asKey, "redis", "LPUSH", Value.get(this.redisBin), Value.get(value));
        return result.longValue();
    }


    public Long llen(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Long result =  (Long) this.asClient.execute(this.writePolicy, asKey, "redis", "LLEN", Value.get(this.redisBin));
        return result.longValue();
    }


    public List<String> lrange(String key, int low, int high) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return (List<String>) this.asClient.execute(this.writePolicy, asKey, "redis", "LRANGE", Value.get(this.redisBin), Value.get(low), Value.get(high));
    }


    public String ltrim(String key, int start, int stop) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return (String) this.asClient.execute(this.writePolicy, asKey, "redis", "LTRIM", Value.get(this.redisBin), Value.get(start), Value.get(stop));
    }


    public String lset(String key, int index, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return (String) this.asClient.execute(this.writePolicy, asKey, "redis", "LSET", Value.get(this.redisBin), Value.get(index), Value.get(value));
    }


    public Object lindex(String key, int index) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return this.asClient.execute(this.writePolicy, asKey, "redis", "LINDEX", Value.get(this.redisBin), Value.get(index));
    }


    public Long lrem(String key, int index, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result =  this.asClient.execute(this.writePolicy, asKey, "redis", "LREM", Value.get(this.redisBin), Value.get(index), Value.get(value));
        return ((Long)result).longValue();
    }


    public String lpop(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<String> result = (List<String>) this.asClient.execute(this.writePolicy, asKey, "redis", "LPOP", Value.get(this.redisBin), Value.get(1));
        if (result.size() == 0) return null;
        return result.get(0);
    }

    /**
     * 没有持久化
     * @param key
     * @return
     */
    public List<String> lpopList(String key, Long count) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<String> result = (List<String>) this.asClient.execute(this.writePolicy, asKey, "redis", "LPOP", Value.get(this.redisBin), Value.get(count));
        if (CollectionUtils.isEmpty(result)){
            return null;
        }
        return result;
    }


    public String rpop(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<String> result = (List<String>) this.asClient.execute(this.writePolicy, asKey, "redis", "RPOP", Value.get(this.redisBin), Value.get(1));
        if (result == null || result.size() == 0) return null;
        return result.get(0);
    }

    public List<String> rpop(String key, Long count) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<String> result = (List<String>) this.asClient.execute(this.writePolicy, asKey, "redis", "RPOP", Value.get(this.redisBin), Value.get(count));
        if (CollectionUtils.isEmpty(result))
            return null;
        return result;
    }


    public String rpoplpush(String popKey, String pushKey) {
        Key asPopKey = new Key(this.namespace, this.redisSet, popKey);
        Key asPushKey = new Key(this.namespace, this.redisSet, pushKey);
        List poppedValue = (List) this.asClient.execute(this.writePolicy, asPopKey, "redis", "RPOP", Value.get(this.redisBin), Value.get(1));
        this.asClient.execute(this.writePolicy, asPushKey, "redis", "LPUSH", Value.get(this.redisBin), Value.get(poppedValue.get(0)));
        return poppedValue.get(0).toString();
    }

    public long lpushx(String key, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result = this.asClient.execute(this.writePolicy, asKey, "redis", "LPUSHX", Value.get(this.redisBin), Value.get(value));
        return ((Long)result).longValue();
    }

    public long rpushx(String key, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result = this.asClient.execute(this.writePolicy, asKey, "redis", "RPUSHX", Value.get(this.redisBin), Value.get(value));
        return ((Long)result).longValue();
    }

    public long linsert(String key, LIST_POSITION position, String piviot,
                        String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result =  this.asClient.execute(this.writePolicy, asKey, "redis", "LINSERT", Value.get(this.redisBin),
                Value.get(position.toString()), Value.get(piviot), Value.get(value));
        return ((Long)result).longValue();
    }

    /*
     * Hash (Map) operations
     */
    public long hset(String key, String field, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result =  this.asClient.execute(this.writePolicy, asKey, "redis", "HSET", Value.get(this.redisBin),
                Value.get(field), Value.get(value));
        return ((Long) result).longValue();
    }


    public Object hget(String key, String field) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return this.asClient.execute(this.writePolicy, asKey, "redis", "HGET", Value.get(this.redisBin),
                Value.get(field));
    }

    public long hsetnx(String key, String field, String value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result = this.asClient.execute(this.writePolicy, asKey, "redis", "HSETNX", Value.get(this.redisBin),
                Value.get(field), Value.get(value));
        return ((Long)result).longValue();
    }

    public String hmset(String key, Map<String, String> hash) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return (String) this.asClient.execute(this.writePolicy, asKey, "redis", "HMSET", Value.get(this.redisBin),
                Value.get(hash));
    }

    public List<String> hmget(String key, String ...fields) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<Object> objects = (List<Object>) this.asClient.execute(this.writePolicy, asKey, "redis", "HMGET", Value.get(this.redisBin),
                Value.get(new ArrayList<String>(Arrays.asList(fields))));
        List<String> result = objects.stream().map(String::valueOf).collect(Collectors.toList());
        return result;
    }

    public long hincrBy(String key, String field, long increment) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result = this.asClient.execute(this.writePolicy, asKey, "redis", "HINCRBY", Value.get(this.redisBin),
                Value.get(field), Value.get(increment));
        return ((Long) result).longValue();
    }

    public boolean hexists(String key, String field) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Integer result = (Integer) this.asClient.execute(this.writePolicy, asKey, "redis", "HEXISTS", Value.get(this.redisBin),
                Value.get(field));
        return (result == 1);
    }


    public Long hdel(String key, String field) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result = this.asClient.execute(this.writePolicy, asKey, "redis", "HDEL", Value.get(this.redisBin),
                Value.get(field));
        return ((Long)result).longValue();
    }

    public Long hlen(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        Object result =  this.asClient.execute(this.writePolicy, asKey, "redis", "HLEN", Value.get(this.redisBin));
        return ((Long)result).longValue();
    }

    public Set<String> hkeys(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<String> result = (List<String>) this.asClient.execute(this.writePolicy, asKey, "redis", "HKEYS", Value.get(this.redisBin));
        return new HashSet<String>(result);
    }

    public List<String> hvals(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<Object> objects = (List<Object>) this.asClient.execute(this.writePolicy, asKey, "redis", "HVALS", Value.get(this.redisBin));
        List<String> result = objects.stream().map(String::valueOf).collect(Collectors.toList());
        return result;
    }

    public Map<String, String> hgetAll(String key) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        List<Object> result = (List<Object>) this.asClient.execute(this.writePolicy, asKey, "redis", "HGETALL", Value.get(this.redisBin));
        List<String> kvList = new ArrayList<>();
        for (Object s : result) {
            kvList.add(String.valueOf(s));
        }
        if (result.size() % 2 != 0)
            throw new AerospikeException("Redis hgetall: Keys and values mismatch");
        String keyString = null;
        boolean isKey = true;
        Map<String, String> mapResult = new HashMap<String, String>();
        for (String keyvalue : kvList){
            if (isKey) {
                keyString = keyvalue;
                isKey = false;
            } else {
                mapResult.put(keyString, keyvalue);
                isKey = true;
            }
        }
        return mapResult;
    }

    public Double hincrByFloat(String key, String field, double value) {
        Key asKey = new Key(this.namespace, this.redisSet, key);
        return (Double) this.asClient.execute(this.writePolicy, asKey, "redis", "HINCRBY", Value.get(this.redisBin),
                Value.get(field), Value.get(value));
    }

    //eq *, front * and tail *, front *, tail *
    private void keysPattern(String pattern, String keyString, Set result){
        if (pattern.equals("*")){// eq *
            result.add(keyString);
        }else if(pattern.startsWith("*") && pattern.endsWith("*")){ //前* 尾*
            String subPattern = pattern.substring(1);
            subPattern = subPattern.substring(subPattern.length(), subPattern.length()-1);
            if (keyString.contains(subPattern)){
                result.add(keyString);
            }
        }else if (pattern.startsWith("*")){ //前*
            String subPattern = pattern.substring(1);
            if (keyString.endsWith(subPattern)){
                result.add(keyString);
            }
        }else if (pattern.endsWith("*")){ //尾*
            String subPattern = pattern.substring(pattern.length(), pattern.length()-1);
            if (keyString.startsWith(subPattern)){
                result.add(keyString);
            }
        }else if (keyString.matches(pattern)){//自定义
            result.add(keyString);
        }
    }

}
