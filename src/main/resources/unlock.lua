-- 释放锁

-- 参数1: 当前key
-- 参数2: 当前线程id

-- 获取redis中存放的id
local id = redis.call("GET", KEYS[1])

-- 判断当前线程中的id是否和redis中存放一致
if(id == ARGV[1]) then
    -- 释放锁
    return redis.call("DEL", KEYS[1])
end
return 0