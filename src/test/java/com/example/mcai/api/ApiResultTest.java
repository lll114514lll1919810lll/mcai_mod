package com.example.mcai.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResultTest {

    @Test
    void ok_createsSuccessResult() {
        ApiResult<String> result = ApiResult.ok("hello");

        assertTrue(result.success());
        assertEquals("hello", result.value());
        assertNull(result.error());
    }

    @Test
    void err_createsErrorResult() {
        ApiResult<String> result = ApiResult.err("something went wrong");

        assertFalse(result.success());
        assertNull(result.value());
        assertEquals("something went wrong", result.error());
    }

    @Test
    void ok_withNullValue() {
        ApiResult<Object> result = ApiResult.ok(null);

        assertTrue(result.success());
        assertNull(result.value());
        assertNull(result.error());
    }

    @Test
    void err_withNullMessage() {
        ApiResult<String> result = ApiResult.err(null);

        assertFalse(result.success());
        assertNull(result.value());
        assertNull(result.error());
    }
}
