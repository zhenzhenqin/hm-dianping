-- 参数列表:
-- 1.优惠券的id
local voucherId = ARGV[1]

--2.用户id
local userId = ARGV[2]


-- key
local stockKey = "seckill:stock:" .. voucherId

-- 订单key
local orderKey = "seckill:order:" .. voucherId


-- 判断当前库存是否充足
local stock = redis.call("get", stockKey)

if not stock or tonumber(stock) <= 0 then
    -- 库存不足 返回1
    return 1
end

-- 库存充足 判断用户是否下单
if (redis.call("sismember", orderKey, userId) == 1) then
    -- 下过单 则返回2
    return 2
end

-- 库存充足 且都未下过单 则下单并将用户id存入订单set中
redis.call("incrby", stockKey, -1) -- 库存减1
redis.call("sadd", orderKey, userId) -- 将用户id存入redis的set中

return 0
