#include <dmsdk/sdk.h>
#include <string.h>
#if defined(DM_PLATFORM_ANDROID)
#include <dmsdk/dlib/android.h>
#endif

namespace {
dmScript::LuaCallbackInfo* g_listener = 0;
dmScript::LuaCallbackInfo* g_pending_listener = 0;
bool g_dispatching = false;
bool g_replace_listener = false;

void ReplaceListener(dmScript::LuaCallbackInfo* listener) {
    if (g_dispatching) {
        if (g_pending_listener) dmScript::DestroyCallback(g_pending_listener);
        g_pending_listener = listener;
        g_replace_listener = true;
        return;
    }
    if (g_listener) dmScript::DestroyCallback(g_listener);
    g_listener = listener;
}

void SetListenerAt(lua_State* L, int index) {
    ReplaceListener(lua_isnoneornil(L, index) ? 0 : dmScript::CreateCallback(L, index));
}

int Failure(lua_State* L, const char* code) {
    lua_pushnil(L);
    lua_pushstring(L, code);
    return 2;
}

#if defined(DM_PLATFORM_ANDROID)
jobject g_bridge = 0;
jmethodID g_initialize = 0, g_show = 0, g_status = 0, g_consent = 0;
jmethodID g_version = 0, g_poll = 0, g_shutdown = 0;

bool CheckException(JNIEnv* env) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionDescribe();
    env->ExceptionClear();
    dmLogError("inmobi_cmp: Java exception; see Android logcat");
    return true;
}

int Command(lua_State* L, jmethodID method, const char* argument) {
    if (!g_bridge || !method) return Failure(L, "bridge_unavailable");
    dmAndroid::ThreadAttacher thread;
    JNIEnv* env = thread.GetEnv();
    jstring value = env->NewStringUTF(argument);
    jstring error = (jstring)env->CallObjectMethod(g_bridge, method, value);
    env->DeleteLocalRef(value);
    if (CheckException(env)) return Failure(L, "java_exception");
    if (error) {
        const char* chars = env->GetStringUTFChars(error, 0);
        int count = Failure(L, chars);
        env->ReleaseStringUTFChars(error, chars);
        env->DeleteLocalRef(error);
        return count;
    }
    lua_pushboolean(L, true);
    return 1;
}

int Read(lua_State* L, jmethodID method, bool json) {
    if (!g_bridge || !method) return Failure(L, "bridge_unavailable");
    dmAndroid::ThreadAttacher thread;
    JNIEnv* env = thread.GetEnv();
    jstring value = (jstring)env->CallObjectMethod(g_bridge, method);
    if (CheckException(env)) return Failure(L, "java_exception");
    if (!value) return Failure(L, "not_available");
    const char* chars = env->GetStringUTFChars(value, 0);
    if (json) dmScript::JsonToLua(L, chars, strlen(chars));
    else lua_pushstring(L, chars);
    env->ReleaseStringUTFChars(value, chars);
    env->DeleteLocalRef(value);
    return 1;
}
#endif

int IsSupported(lua_State* L) {
#if defined(DM_PLATFORM_ANDROID)
    lua_pushboolean(L, true);
#else
    lua_pushboolean(L, false);
#endif
    return 1;
}

int Initialize(lua_State* L) {
    luaL_checktype(L, 1, LUA_TTABLE);
    if (!lua_isnoneornil(L, 2)) luaL_checktype(L, 2, LUA_TFUNCTION);
    lua_settop(L, 2);
    lua_getfield(L, 1, "p_code");
    const char* code = luaL_checkstring(L, -1);
#if defined(DM_PLATFORM_ANDROID)
    int results = Command(L, g_initialize, code);
    if (results == 1) SetListenerAt(L, 2);
    return results;
#else
    (void)code;
    return Failure(L, "unsupported_platform");
#endif
}

int SetListener(lua_State* L) {
    if (!lua_isnoneornil(L, 1)) luaL_checktype(L, 1, LUA_TFUNCTION);
#if defined(DM_PLATFORM_ANDROID)
    SetListenerAt(L, 1);
    lua_pushboolean(L, true);
    return 1;
#else
    return Failure(L, "unsupported_platform");
#endif
}

int ShowGdpr(lua_State* L) {
#if defined(DM_PLATFORM_ANDROID)
    return Command(L, g_show, "gdpr");
#else
    return Failure(L, "unsupported_platform");
#endif
}

int ShowUs(lua_State* L) {
#if defined(DM_PLATFORM_ANDROID)
    return Command(L, g_show, "us");
#else
    return Failure(L, "unsupported_platform");
#endif
}

int GetStatus(lua_State* L) {
#if defined(DM_PLATFORM_ANDROID)
    return Read(L, g_status, true);
#else
    lua_newtable(L);
    lua_pushboolean(L, false); lua_setfield(L, -2, "supported");
    lua_pushboolean(L, false); lua_setfield(L, -2, "initialized");
    lua_pushboolean(L, false); lua_setfield(L, -2, "loaded");
    return 1;
#endif
}

int GetConsent(lua_State* L) {
#if defined(DM_PLATFORM_ANDROID)
    return Read(L, g_consent, true);
#else
    return Failure(L, "unsupported_platform");
#endif
}

int GetVersion(lua_State* L) {
#if defined(DM_PLATFORM_ANDROID)
    return Read(L, g_version, false);
#else
    return Failure(L, "unsupported_platform");
#endif
}

const luaL_reg METHODS[] = {
    {"is_supported", IsSupported}, {"initialize", Initialize},
    {"set_listener", SetListener}, {"show_gdpr", ShowGdpr},
    {"show_us_regulations", ShowUs}, {"get_status", GetStatus},
    {"get_consent", GetConsent}, {"get_sdk_version", GetVersion}, {0, 0}
};

dmExtension::Result ExtensionInitialize(dmExtension::Params* params) {
    luaL_register(params->m_L, "inmobi_cmp", METHODS);
    lua_pop(params->m_L, 1);
#if defined(DM_PLATFORM_ANDROID)
    dmAndroid::ThreadAttacher thread;
    JNIEnv* env = thread.GetEnv();
    jclass cls = dmAndroid::LoadClass(env, "com.defold.inmobicmp.InMobiCmpBridge");
    if (CheckException(env) || !cls) return dmExtension::RESULT_INIT_ERROR;
    jmethodID factory = env->GetStaticMethodID(cls, "getInstance", "(Landroid/app/Activity;)Lcom/defold/inmobicmp/InMobiCmpBridge;");
    g_initialize = env->GetMethodID(cls, "initialize", "(Ljava/lang/String;)Ljava/lang/String;");
    g_show = env->GetMethodID(cls, "show", "(Ljava/lang/String;)Ljava/lang/String;");
    g_status = env->GetMethodID(cls, "getStatus", "()Ljava/lang/String;");
    g_consent = env->GetMethodID(cls, "getConsent", "()Ljava/lang/String;");
    g_version = env->GetMethodID(cls, "getSdkVersion", "()Ljava/lang/String;");
    g_poll = env->GetMethodID(cls, "poll", "()Ljava/lang/String;");
    g_shutdown = env->GetMethodID(cls, "shutdown", "()V");
    if (CheckException(env)) { env->DeleteLocalRef(cls); return dmExtension::RESULT_INIT_ERROR; }
    jobject instance = env->CallStaticObjectMethod(cls, factory, thread.GetActivity()->clazz);
    env->DeleteLocalRef(cls);
    if (CheckException(env) || !instance) return dmExtension::RESULT_INIT_ERROR;
    g_bridge = env->NewGlobalRef(instance);
    env->DeleteLocalRef(instance);
#endif
    return dmExtension::RESULT_OK;
}

dmExtension::Result Update(dmExtension::Params* params) {
#if defined(DM_PLATFORM_ANDROID)
    if (!g_bridge) return dmExtension::RESULT_OK;
    dmAndroid::ThreadAttacher thread;
    JNIEnv* env = thread.GetEnv();
    for (int i = 0; i < 256; ++i) {
        jstring value = (jstring)env->CallObjectMethod(g_bridge, g_poll);
        if (CheckException(env) || !value) break;
        if (g_listener && dmScript::IsCallbackValid(g_listener)) {
            lua_State* L = dmScript::GetCallbackLuaContext(g_listener);
            if (dmScript::SetupCallback(g_listener)) {
                const char* json = env->GetStringUTFChars(value, 0);
                dmScript::JsonToLua(L, json, strlen(json));
                env->ReleaseStringUTFChars(value, json);
                lua_getfield(L, -1, "event");
                lua_getfield(L, -2, "data");
                lua_remove(L, -3);
                g_dispatching = true;
                dmScript::PCall(L, 3, 0);
                dmScript::TeardownCallback(g_listener);
                g_dispatching = false;
                if (g_replace_listener) {
                    ReplaceListener(g_pending_listener);
                    g_pending_listener = 0;
                    g_replace_listener = false;
                }
            }
        }
        env->DeleteLocalRef(value);
    }
#endif
    return dmExtension::RESULT_OK;
}

dmExtension::Result Finalize(dmExtension::Params* params) {
#if defined(DM_PLATFORM_ANDROID)
    if (g_bridge) {
        dmAndroid::ThreadAttacher thread;
        JNIEnv* env = thread.GetEnv();
        env->CallVoidMethod(g_bridge, g_shutdown);
        CheckException(env);
        env->DeleteGlobalRef(g_bridge);
        g_bridge = 0;
    }
#endif
    ReplaceListener(0);
    if (g_pending_listener) dmScript::DestroyCallback(g_pending_listener);
    g_pending_listener = 0;
    g_replace_listener = false;
    return dmExtension::RESULT_OK;
}
} // namespace

DM_DECLARE_EXTENSION(InMobiCmpExt, "inmobi_cmp", 0, 0, ExtensionInitialize, Update, 0, Finalize)
