-- Fixed-window counter behind RateLimitStore.recordHit.
--
-- KEYS[1] the counter, ARGV[1] window length in milliseconds.
-- Returns hits recorded in the current window, this one included.
--
-- One script so the INCR and the PEXPIRE cannot be split by a crash, which
-- would leave a counter with no expiry and lock a key out permanently. Only
-- the first hit sets the expiry, so hammering a refused key cannot push its
-- own window out; the PTTL check recovers a key that somehow has no TTL.

local hits = redis.call('INCR', KEYS[1])

if hits == 1 or redis.call('PTTL', KEYS[1]) < 0 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end

return hits
