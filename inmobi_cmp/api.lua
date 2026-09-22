---@meta
-- Type definitions only. Do not require this file: the module is registered by C++.

---@alias InMobiCmpEvent 'loaded'|'ui_changed'|'gdpr_consent'|'non_iab_consent'|'additional_consent'|'us_consent'|'google_basic_consent'|'region_changed'|'action'|'legacy_ccpa_consent'|'error'
---@alias InMobiCmpCallback fun(self: userdata, event: InMobiCmpEvent, data: table)

---@class InMobiCmpOptions
---@field p_code string InMobi account p-code; an optional leading p- is removed.

---@class InMobiCmpPing
---@field gdpr_applies? boolean Missing means unknown, not false.
---@field us_regulation_applies? boolean
---@field cmp_loaded boolean
---@field cmp_status string SDK enum name.
---@field display_status string SDK enum name.
---@field cmp_id? integer
---@field cmp_version? string
---@field api_version? string
---@field gvl_version? integer
---@field tcf_policy_version? integer

---@class InMobiCmpUi
---@field display_status 'VISIBLE'|'HIDDEN'|'DISABLED'|'DISMISSED'
---@field display_message string
---@field regulation_shown string SDK enum name.
---@field gbc_shown boolean

---@class InMobiCmpStatus
---@field supported boolean
---@field initialized boolean Initialization accepted; not an indication of consent.
---@field loaded boolean SDK configuration loaded; not an indication of consent.
---@field flow_ready? boolean Native flow resolved; check applicable consent signals separately.
---@field form_visible? boolean A native CMP activity exists.
---@field revision? integer Changes when choices, region or form completion change.
---@field failed? boolean An SDK error other than a logo download failure occurred.
---@field cmp? InMobiCmpPing
---@field ui? InMobiCmpUi

---@class InMobiConsentMap
---@field consents table<string, boolean> IDs are string keys.
---@field legitimate_interests table<string, boolean>

---@class InMobiGdprData
---@field gdpr_applies? boolean
---@field tc_string? string
---@field gpp_string? string
---@field cmp_status? string
---@field purpose InMobiConsentMap
---@field vendor InMobiConsentMap
---@field special_features_options table<string, boolean>
---@field publisher table
---@field out_of_band table
---@field tcf_policy_version integer

---@class InMobiUsPrivacy
---@field known boolean Applicable GPP sections could be decoded.
---@field opt_out? boolean Combined sale, sharing, targeted advertising and GPC opt-out.

---@class InMobiConsent
---@field us_privacy? InMobiUsPrivacy
---@field storage table<string, string|number|boolean|table> Exact standard IAB preference keys; absent keys remain absent.
---@field gdpr? InMobiGdprData
---@field non_iab? table SDK non-IAB model, including non_iab_vendor_consents.
---@field additional? {ac_string: string}
---@field us? table SDK US model; numeric choices retain their original encoding.
---@field google_basic? table SDK enum names for the four Google Basic Consent categories.
---@field legacy_ccpa? {usp_string: string}

inmobi_cmp = {}

--- Check whether the current platform implements the SDK.
-- @return boolean true on Android
---@return boolean
function inmobi_cmp.is_supported() end

--- Attach to the early-started CMP and register its event listener.
-- Set the same p_code in game.project at build time for the Android startup provider.
-- Success means accepted, not loaded or consented. Same p-code is idempotent.
-- @param table options p_code configuration
-- @param function callback receives self, event, data (optional)
-- @return boolean|nil accepted
-- @return string error code on failure
---@param options InMobiCmpOptions
---@param callback? InMobiCmpCallback
---@return boolean? accepted
---@return string? error
function inmobi_cmp.initialize(options, callback) end

--- Replace or clear the listener, including from inside a callback.
-- Events are retained until a listener is attached; get_consent retains SDK state.
-- @param function callback listener or nil
-- @return boolean|nil accepted
-- @return string error code on failure
---@param callback? InMobiCmpCallback
---@return boolean? accepted
---@return string? error
function inmobi_cmp.set_listener(callback) end

--- Ask the SDK to reopen its GDPR settings.
-- @return boolean|nil accepted
-- @return string error code on failure
---@return boolean? accepted
---@return string? error
function inmobi_cmp.show_gdpr() end

--- Ask the SDK to show its US Regulations screen.
-- @return boolean|nil accepted
-- @return string error code on failure
---@return boolean? accepted
---@return string? error
function inmobi_cmp.show_us_regulations() end

--- Read the last published CMP loading and UI state.
-- @return table status
-- @return string error code on failure
---@return InMobiCmpStatus? status
---@return string? error
function inmobi_cmp.get_status() end

--- Read SDK model snapshots and current standard consent storage.
-- Unknown or unavailable fields are omitted. This function never grants consent.
-- @return table consent snapshot
-- @return string error code on failure
---@return InMobiConsent? consent
---@return string? error
function inmobi_cmp.get_consent() end

--- Get the native InMobi SDK version.
-- @return string SDK version
-- @return string error code on failure
---@return string? version
---@return string? error
function inmobi_cmp.get_sdk_version() end
