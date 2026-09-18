local M = {}

--- Exercise the actual native module in a desktop Defold engine.
-- @return nil
function M.run()
    assert(inmobi_cmp and not inmobi_cmp.is_supported())
    local status = inmobi_cmp.get_status()
    assert(status.supported == false and status.loaded == false and status.initialized == false)
    local calls = {
        function() return inmobi_cmp.initialize({ p_code = "test" }) end,
        function() return inmobi_cmp.initialize({ p_code = "test" }, function() error("unexpected callback") end) end,
        function() return inmobi_cmp.set_listener(nil) end,
        function() return inmobi_cmp.show_gdpr() end,
        function() return inmobi_cmp.show_us_regulations() end,
        function() return inmobi_cmp.get_consent() end,
        function() return inmobi_cmp.get_sdk_version() end,
    }
    for _, call in ipairs(calls) do
        local value, err = call()
        assert(value == nil and err == "unsupported_platform")
    end
    assert(not pcall(inmobi_cmp.initialize, nil))
    assert(not pcall(inmobi_cmp.initialize, {}))
    assert(not pcall(inmobi_cmp.set_listener, "invalid"))
    print("CMP_DESKTOP_TESTS_PASSED")
end

return M
