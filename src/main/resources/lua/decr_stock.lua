local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
local buy = tonumber(ARGV[1])
if stock < buy then
    return -1
end
redis.call('DECRBY', KEYS[1], buy)
return stock - buy
