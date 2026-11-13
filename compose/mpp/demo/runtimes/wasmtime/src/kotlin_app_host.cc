#include "kotlin_app_host.h"

#include "skiko_host.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <random>
#include <stdexcept>
#include <utility>

#include <wasmtime/anyref.h>
#include <wasmtime/arrayref.h>
#include <wasmtime/externref.h>
#include <wasmtime/global.h>
#include <wasmtime/tag.h>
#include <wasmtime/types/val.h>

namespace {

std::string Name(const wasm_name_t* name) {
    return name == nullptr ? std::string() : std::string(name->data, name->size);
}

std::string ErrorMessage(wasmtime_error_t* error) {
    wasm_byte_vec_t message;
    wasmtime_error_message(error, &message);
    std::string result(message.data, message.size);
    wasm_byte_vec_delete(&message);
    wasmtime_error_delete(error);
    return result;
}

std::string TrapMessage(wasm_trap_t* trap) {
    wasm_byte_vec_t message;
    wasm_trap_message(trap, &message);
    std::string result(message.data, message.size);
    wasm_byte_vec_delete(&message);
    wasm_trap_delete(trap);
    return result;
}

void Check(wasmtime_error_t* error, const std::string& operation) {
    if (error != nullptr) {
        throw std::runtime_error(operation + ": " + ErrorMessage(error));
    }
}

enum class ObjectKind {
    Generic,
    Error,
    Promise,
    Performance,
    Response,
    ArrayBuffer,
    Document,
    Description,
    Canvas,
    Gl,
    GlContext,
    HtmlCanvasClass,
    WebGlClass,
    FinalizationRegistry,
    ContextAttributes,
    WeakRef,
    WeakMap,
    Array,
    Window,
    Location,
    History,
};

} // namespace

KotlinAppHost::Callback::~Callback() {
    if (!wasmtime_anyref_is_null(&function)) {
        wasmtime_anyref_unroot(&function);
    }
}

struct KotlinAppHost::HostValue {
    enum class Kind { String, Number, Boolean, Object, Callback, Poster };
    Kind kind = Kind::Object;
    std::string string;
    double number = 0;
    bool boolean = false;
    ObjectKind object = ObjectKind::Generic;
    int id = 0;
    std::shared_ptr<Callback> callback;
    std::shared_ptr<Callback> target_callback;
    std::vector<std::string> string_array;
    wasmtime_val_t target_val{};
    bool has_target_val = false;
    wasmtime_val_t kotlin_exception{};
    bool has_kotlin_exception = false;

    ~HostValue() {
        if (has_target_val) {
            wasmtime_val_unroot(&target_val);
        }
        if (has_kotlin_exception) {
            wasmtime_val_unroot(&kotlin_exception);
        }
    }

    static HostValue* String(std::string value) {
        auto* result = new HostValue;
        result->kind = Kind::String;
        result->string = std::move(value);
        return result;
    }
    static HostValue* Number(double value) {
        auto* result = new HostValue;
        result->kind = Kind::Number;
        result->number = value;
        return result;
    }
    static HostValue* BooleanValue(bool value) {
        auto* result = new HostValue;
        result->kind = Kind::Boolean;
        result->boolean = value;
        return result;
    }
    static HostValue* Object(ObjectKind kind, int id = 0) {
        auto* result = new HostValue;
        result->object = kind;
        result->id = id;
        return result;
    }
    static HostValue* StringArray(std::vector<std::string> items) {
        auto* result = new HostValue;
        result->object = ObjectKind::Array;
        result->string_array = std::move(items);
        return result;
    }
    static HostValue* WeakRefValue(const wasmtime_val_t& target) {
        auto* result = new HostValue;
        result->object = ObjectKind::WeakRef;
        wasmtime_val_clone(&target, &result->target_val);
        result->has_target_val = true;
        return result;
    }
    static HostValue* CallbackValue(std::shared_ptr<Callback> value) {
        auto* result = new HostValue;
        result->kind = Kind::Callback;
        result->callback = std::move(value);
        return result;
    }
    static HostValue* Poster(std::shared_ptr<Callback> target) {
        auto* result = new HostValue;
        result->kind = Kind::Poster;
        result->target_callback = std::move(target);
        return result;
    }
};

struct KotlinAppHost::Binding {
    KotlinAppHost* host;
    std::string module;
    std::string field;
    std::string name;
};

KotlinAppHost::KotlinAppHost(wasmtime_context_t* context, SkikoHost* skiko)
    : context_(context), skiko_(skiko) {}

KotlinAppHost::~KotlinAppHost() = default;

void KotlinAppHost::DeleteHostValue(void* value) {
    delete static_cast<HostValue*>(value);
}

void KotlinAppHost::DeleteBinding(void* value) {
    delete static_cast<Binding*>(value);
}

void KotlinAppHost::SetExternResult(
    wasmtime_val_t* result,
    HostValue* value
) {
    result->kind = WASMTIME_EXTERNREF;
    if (!wasmtime_externref_new(context_, value, DeleteHostValue,
                                &result->of.externref)) {
        delete value;
        throw std::runtime_error("Could not allocate an externref");
    }
}

KotlinAppHost::HostValue* KotlinAppHost::GetHostValue(
    const wasmtime_val_t& value
) const {
    if (value.kind != WASMTIME_EXTERNREF ||
        wasmtime_externref_is_null(&value.of.externref)) {
        return nullptr;
    }
    return static_cast<HostValue*>(
        wasmtime_externref_data(context_, &value.of.externref));
}

std::string KotlinAppHost::GetString(const wasmtime_val_t& value) const {
    HostValue* host = GetHostValue(value);
    if (host == nullptr) return "null";
    if (host->kind == HostValue::Kind::String) return host->string;
    if (host->kind == HostValue::Kind::Number) {
        return std::to_string(host->number);
    }
    if (host->kind == HostValue::Kind::Boolean) {
        return host->boolean ? "true" : "false";
    }
    if (!host->string.empty()) return host->string;
    return "[object Object]";
}

wasm_trap_t* KotlinAppHost::Dispatch(
    void* environment,
    wasmtime_caller_t* caller,
    const wasmtime_val_t* args,
    size_t nargs,
    wasmtime_val_t* results,
    size_t nresults
) {
    auto* binding = static_cast<Binding*>(environment);
    if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr) {
        std::cerr << "[Kotlin host] " << binding->name << '\n';
    }
    try {
        return binding->host->CallHost(
            *binding, caller, args, nargs, results, nresults);
    } catch (const std::exception& exception) {
        return SkikoHost::Trap(binding->name + ": " + exception.what());
    }
}

void KotlinAppHost::RegisterImports(
    wasmtime_linker_t* linker,
    const wasmtime_module_t* application_module
) {
    wasm_importtype_vec_t imports;
    wasmtime_module_imports(application_module, &imports);

    size_t functions = 0;
    size_t constants = 0;
    size_t tags = 0;
    for (size_t index = 0; index < imports.size; ++index) {
        const wasm_importtype_t* import = imports.data[index];
        const std::string module = Name(wasm_importtype_module(import));
        const std::string field = Name(wasm_importtype_name(import));
        const wasm_externtype_t* external = wasm_importtype_type(import);

        if (module == "./skiko.mjs") continue;

        if (module == "'") {
            const wasm_globaltype_t* type =
                wasm_externtype_as_globaltype_const(external);
            if (type == nullptr) {
                wasm_importtype_vec_delete(&imports);
                throw std::runtime_error("String constant import is not global");
            }
            wasmtime_val_t value{};
            SetExternResult(&value, HostValue::String(field));
            wasmtime_global_t global;
            Check(
                wasmtime_global_new(context_, type, &value, &global),
                "Creating string constant"
            );
            wasmtime_val_unroot(&value);
            wasmtime_extern_t item{};
            item.kind = WASMTIME_EXTERN_GLOBAL;
            item.of.global = global;
            Check(
                wasmtime_linker_define(
                    linker, context_, module.data(), module.size(),
                    field.data(), field.size(), &item),
                "Defining string constant"
            );
            ++constants;
            continue;
        }

        if (module == "intrinsics") {
            const wasm_tagtype_t* type = wasm_externtype_as_tagtype_const(external);
            if (type == nullptr) {
                wasm_importtype_vec_delete(&imports);
                throw std::runtime_error("intrinsics.tag is not a tag");
            }
            wasmtime_tag_t tag;
            Check(wasmtime_tag_new(context_, type, &tag), "Creating JS tag");
            wasmtime_extern_t item{};
            item.kind = WASMTIME_EXTERN_TAG;
            item.of.tag = tag;
            Check(
                wasmtime_linker_define(
                    linker, context_, module.data(), module.size(),
                    field.data(), field.size(), &item),
                "Defining JS tag"
            );
            ++tags;
            continue;
        }

        const wasm_functype_t* function =
            wasm_externtype_as_functype_const(external);
        if (function == nullptr) {
            wasm_importtype_vec_delete(&imports);
            throw std::runtime_error(
                "Unsupported application import " + module + "." + field);
        }
        auto* binding = new Binding{this, module, field, module + "." + field};

        wasmtime_error_t* error = wasmtime_linker_define_func(
            linker,
            module.data(), module.size(),
            field.data(), field.size(),
            function,
            Dispatch,
            binding,
            DeleteBinding
        );
        if (error != nullptr) {
            delete binding;
            wasm_importtype_vec_delete(&imports);
            throw std::runtime_error(ErrorMessage(error));
        }
        ++functions;
    }
    wasm_importtype_vec_delete(&imports);
    std::cout << "Registered " << functions << " Kotlin host functions, "
              << constants << " string constants, and " << tags << " tag\n";
}

void KotlinAppHost::AttachInstance(const wasmtime_instance_t& instance) {
    instance_ = instance;
    attached_ = true;
}

void KotlinAppHost::Start() {
    if (!attached_) throw std::runtime_error("Application instance is not attached");
    wasmtime_extern_t item;
    if (!wasmtime_instance_export_get(
            context_, &instance_, "_start", sizeof("_start") - 1, &item) ||
        item.kind != WASMTIME_EXTERN_FUNC) {
        throw std::runtime_error("Application does not export _start");
    }
    std::cout << "Kotlin host bridge revision 3\n";
    wasm_trap_t* trap = nullptr;
    wasmtime_error_t* error = wasmtime_func_call(
        context_, &item.of.func, nullptr, 0, nullptr, 0, &trap);
    if (error != nullptr) throw std::runtime_error(ErrorMessage(error));
    if (trap != nullptr) throw std::runtime_error(TrapMessage(trap));
    DrainReadyCallbacks();
}

bool KotlinAppHost::Invoke(
    const std::shared_ptr<Callback>& callback,
    double timestamp
) {
    if (!callback) return false;
    wasmtime_func_t target_func{};

    if (callback->has_trampoline) {
        target_func = callback->trampoline;
    } else {
        const char* export_name = nullptr;
        if (callback->signature == Callback::Signature::UnitToUnit) {
            export_name = "__callFunction_(()->Unit)";
        } else if (callback->signature == Callback::Signature::JsToJs) {
            export_name = "__callFunction_((Js?)->Js?)";
        } else if (callback->signature == Callback::Signature::JsToUnit) {
            export_name = "__callFunction_((Js)->Unit)";
        } else {
            export_name = "__callFunction_((Double)->Unit)";
        }

        wasmtime_extern_t item;
        if (!wasmtime_instance_export_get(
                context_, &instance_, export_name, std::strlen(export_name), &item) ||
            item.kind != WASMTIME_EXTERN_FUNC) {
            std::cerr << "[KotlinAppHost] Missing callback export " << export_name << std::endl;
            return false;
        }
        target_func = item.of.func;
    }

    wasm_functype_t* func_type = wasmtime_func_type(context_, &target_func);
    const wasm_valtype_vec_t* results_types = wasm_functype_results(func_type);
    const size_t result_count = results_types != nullptr ? results_types->size : 0;
    const wasm_valtype_vec_t* params_types = wasm_functype_params(func_type);
    const size_t param_count = params_types != nullptr ? params_types->size : 0;
    const bool is_f64_arg = (callback->signature == Callback::Signature::DoubleToUnit);
    wasm_functype_delete(func_type);

    wasmtime_val_t args[2]{};
    args[0].kind = WASMTIME_ANYREF;
    wasmtime_anyref_clone(&callback->function, &args[0].of.anyref);
    if (param_count > 1) {
        if (is_f64_arg) {
            args[1].kind = WASMTIME_F64;
            args[1].of.f64 = timestamp;
        } else {
            SetExternResult(&args[1], HostValue::Object(ObjectKind::Generic));
        }
    }

    wasmtime_val_t result{};
    wasm_trap_t* trap = nullptr;
    wasmtime_error_t* error = wasmtime_func_call(
        context_, &target_func, args, std::min<size_t>(param_count, 2),
        result_count ? &result : nullptr, result_count, &trap);
    wasmtime_val_unroot(&args[0]);
    if (param_count > 1 && !is_f64_arg) {
        wasmtime_val_unroot(&args[1]);
    }
    bool ok = true;
    if (error != nullptr) {
        ok = false;
        if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr) {
            std::cerr << "[KotlinAppHost] Callback error: " << ErrorMessage(error) << std::endl;
        } else {
            wasmtime_error_delete(error);
        }
    }
    if (trap != nullptr) {
        ok = false;
        if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr) {
            std::cerr << "[KotlinAppHost] Callback trap: " << TrapMessage(trap) << std::endl;
        } else {
            wasm_trap_delete(trap);
        }
    }
    if (result_count != 0) wasmtime_val_unroot(&result);
    return ok;
}

void KotlinAppHost::DrainReadyCallbacks() {
    size_t iterations = 0;
    while (!ready_callbacks_.empty() && iterations++ < 1000) {
        auto callback = ready_callbacks_.front();
        ready_callbacks_.pop_front();
        Invoke(callback, 0.0);
    }
}

void KotlinAppHost::RunExpiredTimers(const std::chrono::steady_clock::time_point& now) {
    if (timers_.empty()) return;
    std::vector<std::shared_ptr<Callback>> expired;
    std::vector<Timer> remaining;
    for (auto& timer : timers_) {
        if (now >= timer.due_time) {
            if (timer.callback) {
                expired.push_back(timer.callback);
            }
            if (timer.is_interval) {
                timer.due_time = now + std::chrono::milliseconds(timer.interval_ms);
                remaining.push_back(timer);
            }
        } else {
            remaining.push_back(timer);
        }
    }
    timers_ = std::move(remaining);
    for (const auto& cb : expired) {
        try {
            Invoke(cb, 0.0);
        } catch (const std::exception& e) {
            std::cerr << "[KotlinAppHost] Timer callback error: " << e.what() << std::endl;
        }
    }
}

std::chrono::steady_clock::time_point KotlinAppHost::NextDeadline(
    const std::chrono::steady_clock::time_point& default_deadline
) const {
    auto deadline = default_deadline;
    for (const auto& timer : timers_) {
        if (timer.due_time < deadline) {
            deadline = timer.due_time;
        }
    }
    return deadline;
}

size_t KotlinAppHost::AnimationCallbackCount() const {
    return animation_callbacks_.size();
}

void KotlinAppHost::RunNextAnimationCallback(double timestamp_millis) {
    if (animation_callbacks_.empty()) return;
    const std::shared_ptr<Callback> callback = animation_callbacks_.front();
    animation_callbacks_.pop_front();
    Invoke(callback, timestamp_millis);
}

void KotlinAppHost::DispatchPointerEvent(
    int32_t event_type,
    double x,
    double y,
    int32_t button,
    double scroll_delta_x,
    double scroll_delta_y
) {
    if (!pointer_callback_) return;

    wasmtime_func_t target_func{};
    if (pointer_callback_->has_trampoline) {
        target_func = pointer_callback_->trampoline;
    } else {
        const char* export_name = "__callFunction_((Int,Double,Double,Int,Double,Double)->Unit)";
        wasmtime_extern_t item;
        if (!wasmtime_instance_export_get(
                context_, &instance_, export_name, std::strlen(export_name), &item) ||
            item.kind != WASMTIME_EXTERN_FUNC) {
            std::cerr << "[KotlinAppHost] Missing callback export " << export_name << std::endl;
            return;
        }
        target_func = item.of.func;
    }

    wasm_functype_t* func_type = wasmtime_func_type(context_, &target_func);
    const wasm_valtype_vec_t* params_types = wasm_functype_params(func_type);
    const size_t param_count = params_types != nullptr ? params_types->size : 0;
    wasm_functype_delete(func_type);

    wasmtime_val_t args[7]{};
    args[0].kind = WASMTIME_ANYREF;
    wasmtime_anyref_clone(&pointer_callback_->function, &args[0].of.anyref);
    args[1].kind = WASMTIME_I32;
    args[1].of.i32 = event_type;
    args[2].kind = WASMTIME_F64;
    args[2].of.f64 = x;
    args[3].kind = WASMTIME_F64;
    args[3].of.f64 = y;
    args[4].kind = WASMTIME_I32;
    args[4].of.i32 = button;
    args[5].kind = WASMTIME_F64;
    args[5].of.f64 = scroll_delta_x;
    args[6].kind = WASMTIME_F64;
    args[6].of.f64 = scroll_delta_y;

    wasm_trap_t* trap = nullptr;
    wasmtime_error_t* error = wasmtime_func_call(
        context_, &target_func, args, std::min<size_t>(param_count, 7),
        nullptr, 0, &trap
    );
    wasmtime_val_unroot(&args[0]);
    if (error != nullptr) {
        std::cerr << "[KotlinAppHost] Pointer dispatch error: " << ErrorMessage(error) << std::endl;
    }
    if (trap != nullptr) {
        std::cerr << "[KotlinAppHost] Pointer dispatch trap: " << TrapMessage(trap) << std::endl;
    }
    DrainReadyCallbacks();
    RunExpiredTimers(std::chrono::steady_clock::now());
    DrainReadyCallbacks();
}

const std::string& KotlinAppHost::Title() const {
    return title_;
}

wasm_trap_t* KotlinAppHost::CallHost(
    const Binding& binding,
    wasmtime_caller_t*,
    const wasmtime_val_t* args,
    size_t nargs,
    wasmtime_val_t* results,
    size_t nresults
) {
    const std::string& qualified_name = binding.name;
    const std::string& name = binding.field.empty() ? binding.name : binding.field;

    auto string_result = [&](std::string value) {
        SetExternResult(&results[0], HostValue::String(std::move(value)));
    };
    auto object_result = [&](ObjectKind kind, int id = 0) {
        SetExternResult(&results[0], HostValue::Object(kind, id));
    };
    auto null_result = [&] {
        results[0].kind = WASMTIME_EXTERNREF;
        wasmtime_externref_set_null(&results[0].of.externref);
    };

    if (qualified_name == "wasm:js-string.length") {
        const std::string value = GetString(args[0]);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = static_cast<int32_t>(value.size());
        if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr &&
            value.find("[a-z0-9]") != std::string::npos) {
            std::cerr << "[target string] length=" << value.size()
                      << " value=" << value << '\n';
        }
    } else if (qualified_name == "wasm:js-string.concat") {
        string_result(GetString(args[0]) + GetString(args[1]));
    } else if (qualified_name == "wasm:js-string.charCodeAt") {
        const std::string value = GetString(args[0]);
        const size_t index = static_cast<uint32_t>(args[1].of.i32);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = index < value.size()
            ? static_cast<unsigned char>(value[index]) : 0;
        if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr &&
            value.find("[a-z0-9]") != std::string::npos) {
            std::cerr << "[target string] charCodeAt(" << index << ")="
                      << results[0].of.i32 << '\n';
        }
    } else if (qualified_name == "wasm:js-string.substring") {
        const std::string value = GetString(args[0]);
        const size_t begin = std::min<size_t>(
            static_cast<uint32_t>(args[1].of.i32), value.size());
        const size_t end = std::min<size_t>(
            static_cast<uint32_t>(args[2].of.i32), value.size());
        string_result(value.substr(std::min(begin, end),
                                   std::max(begin, end) - std::min(begin, end)));
    } else if (qualified_name == "wasm:js-string.compare" ||
               qualified_name == "wasm:js-string.equals") {
        const std::string left = GetString(args[0]);
        const std::string right = GetString(args[1]);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = qualified_name.ends_with("equals")
            ? (left == right)
            : (left == right ? 0 : (left < right ? -1 : 1));
    } else if (qualified_name == "wasm:js-string.fromCharCodeArray") {
        wasmtime_arrayref_t array;
        if (!wasmtime_anyref_as_array(context_, &args[0].of.anyref, &array)) {
            return SkikoHost::Trap("fromCharCodeArray expected an array");
        }
        std::string value;
        for (uint32_t index = static_cast<uint32_t>(args[1].of.i32);
             index < static_cast<uint32_t>(args[2].of.i32); ++index) {
            wasmtime_val_t element;
            if (wasmtime_error_t* error =
                    wasmtime_arrayref_get(context_, &array, index, &element)) {
                wasmtime_arrayref_unroot(&array);
                throw std::runtime_error(
                    "Reading UTF-16 character array: " + ErrorMessage(error));
            }
            value.push_back(static_cast<char>(element.of.i32));
            wasmtime_val_unroot(&element);
        }
        wasmtime_arrayref_unroot(&array);
        string_result(std::move(value));
    } else if (qualified_name == "wasm:js-string.intoCharCodeArray") {
        const std::string value = GetString(args[0]);
        wasmtime_arrayref_t array;
        if (!wasmtime_anyref_as_array(context_, &args[1].of.anyref, &array)) {
            return SkikoHost::Trap("intoCharCodeArray expected an array");
        }
        uint32_t index = static_cast<uint32_t>(args[2].of.i32);
        for (unsigned char character : value) {
            wasmtime_val_t element{};
            element.kind = WASMTIME_I32;
            element.of.i32 = character;
            if (wasmtime_error_t* error =
                    wasmtime_arrayref_set(context_, &array, index++, &element)) {
                wasmtime_arrayref_unroot(&array);
                throw std::runtime_error(
                    "Writing UTF-16 character array: " + ErrorMessage(error));
            }
        }
        wasmtime_arrayref_unroot(&array);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = static_cast<int32_t>(value.size());
    } else if (name == "kotlin.wasm.internal.getJsEmptyString" ||
               name == "kotlin.js.stackPlaceHolder_js_code") {
        string_result("");
    } else if (name == "kotlin.wasm.internal.externrefToInt") {
        HostValue* value = GetHostValue(args[0]);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = value && value->kind == HostValue::Kind::Number
            ? static_cast<int32_t>(value->number) : 0;
    } else if (name == "kotlin.wasm.internal.externrefToString" ||
               name == "kotlin.wasm.internal.itoa32_$external_fun" ||
               name == "kotlin.wasm.internal.utoa32_$external_fun") {
        if (args[0].kind == WASMTIME_I32) string_result(std::to_string(args[0].of.i32));
        else string_result(GetString(args[0]));
    } else if (name == "kotlin.wasm.internal.externrefEquals") {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = GetString(args[0]) == GetString(args[1]);
    } else if (name == "kotlin.wasm.internal.externrefHashCode") {
        results[0].kind = WASMTIME_I32;
        uint32_t hash = 0;
        for (unsigned char c : GetString(args[0])) hash = hash * 31 + c;
        results[0].of.i32 = static_cast<int32_t>(hash);
    } else if (name == "kotlin.wasm.internal.isNullish") {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = wasmtime_externref_is_null(&args[0].of.externref);
        if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr) {
            std::cerr
                << "[isNullish] kind=" << static_cast<int>(args[0].kind)
                << " store=" << args[0].of.externref.store_id
                << " result=" << results[0].of.i32 << '\n';
        }
    } else if (name == "kotlin.wasm.internal.kotlinUIntToJsNumberUnsafe") {
        SetExternResult(&results[0], HostValue::Number(
            static_cast<uint32_t>(args[0].of.i32)));
    } else if (name == "kotlin.wasm.internal.getCachedJsObject_$external_fun") {
        wasmtime_val_clone(&args[1], &results[0]);
    } else if (name == "kotlin.io.printlnImpl") {
        std::cout << GetString(args[0]) << '\n';
    } else if (name == "kotlin.random.initialSeed") {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 0x534b494b;
    } else if (name.find("__convertKotlinClosureToJsClosure_") != std::string::npos) {
        auto callback = std::make_shared<Callback>();
        if (name.find("Double") != std::string::npos) {
            callback->signature = Callback::Signature::DoubleToUnit;
        } else if (name.find("(()->Unit)") != std::string::npos) {
            callback->signature = Callback::Signature::UnitToUnit;
        } else if (name.find("(Js?)->Js?") != std::string::npos || name.find("(Js)->Js?") != std::string::npos) {
            callback->signature = Callback::Signature::JsToJs;
        } else if (name.find("->Unit") != std::string::npos) {
            callback->signature = Callback::Signature::JsToUnit;
        }
        wasmtime_anyref_clone(&args[0].of.anyref, &callback->function);
        if (nargs >= 2 && args[1].kind == WASMTIME_FUNCREF) {
            callback->trampoline = args[1].of.funcref;
            callback->has_trampoline = true;
        }
        SetExternResult(&results[0], HostValue::CallbackValue(std::move(callback)));
    } else if (name == "kotlin.js.jsCatch" || name == "org.jetbrains.skiko.jsCatch") {
        HostValue* closure = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (closure && closure->callback) {
            last_thrown_exception_ = nullptr;
            bool ok = Invoke(closure->callback, 0.0);
            if (!ok || last_thrown_exception_ != nullptr) {
                HostValue* err = last_thrown_exception_;
                last_thrown_exception_ = nullptr;
                if (!err) {
                    err = HostValue::Object(ObjectKind::Error);
                    err->string = "Captured JS/Kotlin exception";
                }
                SetExternResult(&results[0], err);
                return nullptr;
            }
        }
        null_result();
        return nullptr;
    } else if (name == "kotlin.js.__callJsClosure_(()->Unit)" ||
               name == "kotlinx.coroutines.__callJsClosure_(()->Unit)") {
        HostValue* closure = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (closure) {
            if (closure->kind == HostValue::Kind::Poster) {
                if (closure->target_callback) {
                    ready_callbacks_.push_back(closure->target_callback);
                } else if (coroutine_window_process_) {
                    ready_callbacks_.push_back(coroutine_window_process_);
                }
            } else if (closure->callback) {
                Invoke(closure->callback, 0.0);
            }
        }
    } else if (name == "kotlinx.coroutines.tryGetProcess" ||
               name == "kotlinx.coroutines.tryGetWindow") {
        object_result(ObjectKind::Generic);
    } else if (name == "kotlinx.coroutines.nextTick_$external_fun") {
        HostValue* callback = nargs > 1 ? GetHostValue(args[1]) : (nargs > 0 ? GetHostValue(args[0]) : nullptr);
        if (callback && callback->callback) ready_callbacks_.push_back(callback->callback);
    } else if (name == "kotlinx.coroutines.createScheduleMessagePoster") {
        HostValue* process_cb = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        SetExternResult(&results[0], HostValue::Poster(process_cb ? process_cb->callback : nullptr));
    } else if (name == "kotlinx.coroutines.createRescheduleMessagePoster") {
        SetExternResult(&results[0], HostValue::Poster(nullptr));
    } else if (name == "kotlinx.coroutines.subscribeToWindowMessages") {
        HostValue* process_cb = nargs > 1 ? GetHostValue(args[1]) : (nargs > 0 ? GetHostValue(args[0]) : nullptr);
        if (process_cb && process_cb->callback) {
            coroutine_window_process_ = process_cb->callback;
        }
    } else if (name == "kotlin.js.then_$external_fun" ||
               name == "kotlin.js.then_$external_fun_1") {
        HostValue* callback = nargs > 1 ? GetHostValue(args[1]) : nullptr;
        if (callback && callback->callback) ready_callbacks_.push_back(callback->callback);
        // JavaScript Promise.then always returns a new Promise. Kotlin models
        // that result as non-null even when the caller ignores it.
        object_result(ObjectKind::Promise);
    } else if (name == "kotlin.js.catch_$external_fun") {
        object_result(ObjectKind::Promise);
    } else if (name.find("isSdlEnvironment") != std::string::npos) {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 1;
    } else if (name.find("getCanvasWidth") != std::string::npos) {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 2048;
    } else if (name.find("getCanvasHeight") != std::string::npos) {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 1536;
    } else if (name.find("registerPointerCallback") != std::string::npos) {
        HostValue* callback_val = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (callback_val && callback_val->callback) {
            pointer_callback_ = callback_val->callback;
        }
    } else if (name == "org.w3c.performance.performance_$external_prop_getter" ||
               name == "performance_$external_prop_getter" ||
               name == "kotlin.time.tryGetPerformance" ||
               name.find("tryGetPerformance") != std::string::npos) {
        object_result(ObjectKind::Performance);
    } else if (name == "org.w3c.performance.now_$external_fun" ||
               name == "now_$external_fun" ||
               name == "kotlin.time.getPerformanceNow" ||
               name.find("getPerformanceNow") != std::string::npos ||
               name == "kotlin.time.dateNow" ||
               name.find("dateNow") != std::string::npos ||
               name == "kotlin.time.getTime_$external_fun" ||
               name.find("getTime_$external_fun") != std::string::npos ||
               name == "org.jetbrains.skiko.currentNanoTimeWindowPerformance" ||
               name == "androidx.compose.material3.internal.now_$external_fun" ||
               name == "androidx.compose.ui.platform.timeRemaining_$external_fun" ||
               name == "org.w3c.dom.events.timeStamp_$external_prop_getter" ||
               name.find("currentTimeMillis") != std::string::npos) {
        if (nresults > 0) {
            const auto now = std::chrono::steady_clock::now().time_since_epoch();
            results[0].kind = WASMTIME_F64;
            if (name == "org.jetbrains.skiko.currentNanoTimeWindowPerformance") {
                results[0].of.f64 = static_cast<double>(std::chrono::duration_cast<std::chrono::nanoseconds>(now).count());
            } else if (name == "androidx.compose.ui.platform.timeRemaining_$external_fun") {
                results[0].of.f64 = 16.6;
            } else {
                results[0].of.f64 = std::chrono::duration<double, std::milli>(now).count();
            }
        }
    } else if (name.find("setTimeout") != std::string::npos || name.find("setInterval") != std::string::npos) {
        HostValue* cb_val = nullptr;
        double delay_ms = 0.0;
        for (size_t i = 0; i < nargs; ++i) {
            HostValue* val = GetHostValue(args[i]);
            if (val && val->callback) {
                cb_val = val;
            } else if (args[i].kind == WASMTIME_I32) {
                delay_ms = args[i].of.i32;
            } else if (args[i].kind == WASMTIME_F64) {
                delay_ms = args[i].of.f64;
            }
        }
        if (cb_val && cb_val->callback) {
            const uint32_t id = next_timer_id_++;
            const bool is_int = name.find("setInterval") != std::string::npos;
            const uint32_t ms = static_cast<uint32_t>(std::max(0.0, delay_ms));
            const auto due = std::chrono::steady_clock::now() + std::chrono::milliseconds(ms);
            timers_.push_back({id, due, cb_val->callback, is_int, ms});
            if (nresults > 0) {
                results[0].kind = WASMTIME_I32;
                results[0].of.i32 = static_cast<int32_t>(id);
            }
        } else {
            if (nresults > 0) {
                results[0].kind = WASMTIME_I32;
                results[0].of.i32 = 0;
            }
        }
    } else if (name.find("clearTimeout") != std::string::npos || name.find("clearInterval") != std::string::npos) {
        int32_t timer_id = 0;
        for (size_t i = 0; i < nargs; ++i) {
            if (args[i].kind == WASMTIME_I32) timer_id = args[i].of.i32;
        }
        if (timer_id > 0) {
            timers_.erase(
                std::remove_if(timers_.begin(), timers_.end(), [timer_id](const Timer& t) {
                    return t.id == static_cast<uint32_t>(timer_id);
                }),
                timers_.end()
            );
        }
    } else if (name.find("schedulerPostTask") != std::string::npos ||
               name.find("requestIdleCallback") != std::string::npos) {
        HostValue* block = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (block && block->callback) {
            ready_callbacks_.push_back(block->callback);
        }
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 1;
        }
    } else if (name.find("cancelIdleCallback") != std::string::npos) {
        // no-op
    } else if (name.find("didTimeout") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 0;
        }
    } else if (name == "org.jetbrains.skiko.wasm.awaitSkiko_$external_prop_getter") {
        object_result(ObjectKind::Promise);
    } else if (name == "kotlinx.browser.document_$external_prop_getter" ||
               name == "org.w3c.dom.document_$external_prop_getter") {
        object_result(ObjectKind::Document);
    } else if (name == "org.w3c.dom.body_$external_prop_getter" ||
               name.find("body_$external_prop_getter") != std::string::npos) {
        object_result(ObjectKind::Generic);
    } else if (name.find("createElement_$external_fun") != std::string::npos ||
               name.find("createElement") != std::string::npos) {
        object_result(ObjectKind::Generic);
    } else if (name.find("getComputedStyle") != std::string::npos) {
        object_result(ObjectKind::Generic);
    } else if (name.find("style_$external_prop_getter") != std::string::npos) {
        object_result(ObjectKind::Generic);
    } else if (name.find("fontSize_$external_prop_getter") != std::string::npos) {
        string_result("16px");
    } else if (name.find("DOM_DELTA_PIXEL") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 0;
        }
    } else if (name.find("DOM_DELTA_LINE") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 1;
        }
    } else if (name.find("DOM_DELTA_PAGE") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 2;
        }
    } else if (name.find("deltaMode") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 0;
        }
    } else if (name.find("deltaX_$external_prop_getter") != std::string::npos ||
               name.find("deltaY_$external_prop_getter") != std::string::npos ||
               name.find("deltaZ_$external_prop_getter") != std::string::npos ||
               name.find("wheelDeltaX") != std::string::npos ||
               name.find("wheelDeltaY") != std::string::npos ||
               name.find("wheelDelta") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_F64;
            results[0].of.f64 = 0.0;
        }
    } else if (name == "kotlinx.browser.window_$external_prop_getter" ||
               name == "org.w3c.dom.window_$external_prop_getter") {
        object_result(ObjectKind::Generic);
    } else if (name == "org.w3c.dom.getElementById_$external_fun") {
        const std::string id = GetString(args[1]);
        if (id == "description") object_result(ObjectKind::Description);
        else object_result(ObjectKind::Canvas, 1);
    } else if (name == "org.w3c.dom.title_$external_prop_setter" ||
               name == "org.w3c.dom.innerHTML_$external_prop_setter") {
        title_ = GetString(args[1]);
    } else if (name == "org.w3c.dom.width_$external_prop_getter" ||
               name == "org.w3c.dom.height_$external_prop_getter") {
        HostValue* canvas = GetHostValue(args[0]);
        const bool width = name.find("width_") != std::string::npos;
        results[0].kind = WASMTIME_I32;
        if (!canvas || canvas->object != ObjectKind::Canvas) results[0].of.i32 = 0;
        else results[0].of.i32 = width ? 2048 : 1536;
    } else if (name == "org.w3c.dom.HTMLCanvasElement_$external_class_instanceof") {
        HostValue* value = GetHostValue(args[0]);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = value && value->object == ObjectKind::Canvas;
    } else if (name == "org.w3c.dom.HTMLCanvasElement_$external_class_get") {
        object_result(ObjectKind::HtmlCanvasClass);
    } else if (name == "org.w3c.dom.Performance_$external_class_instanceof" ||
               name == "org.w3c.dom.Window_$external_class_instanceof" ||
               name == "org.w3c.dom.Document_$external_class_instanceof" ||
               name == "org.w3c.dom.EventTarget_$external_class_instanceof" ||
               name == "org.w3c.dom.Node_$external_class_instanceof" ||
               name == "org.w3c.dom.Element_$external_class_instanceof" ||
               name == "org.w3c.dom.HTMLElement_$external_class_instanceof") {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 1;
    } else if (name == "org.jetbrains.skiko.GL_$external_prop_getter") {
        object_result(ObjectKind::Gl);
    } else if (name == "org.jetbrains.skiko.createContext_$external_fun") {
        HostValue* canvas = GetHostValue(args[1]);
        if (!canvas || canvas->object != ObjectKind::Canvas) {
            return SkikoHost::Trap("createContext expected a canvas");
        }
        const int width = 2048;
        const int height = 1536;
        std::string error;
        const int id = skiko_->CreateCanvasContext(width, height, error);
        if (id == 0) return SkikoHost::Trap(error);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = id;
    } else if (name == "org.jetbrains.skiko.makeContextCurrent_$external_fun") {
        std::string error;
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = skiko_->MakeContextCurrent(args[1].of.i32, error) ? 1 : 0;
        if (!error.empty()) return SkikoHost::Trap(error);
    } else if (name == "org.jetbrains.skiko.currentGLContext") {
        object_result(ObjectKind::GlContext, skiko_->CurrentContextId());
    } else if (name == "org.khronos.webgl.SAMPLES_$external_prop_getter") {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 0x80A9;
    } else if (name == "org.khronos.webgl.getParameter_$external_fun") {
        SetExternResult(&results[0], HostValue::Number(0));
    } else if (name == "org.khronos.webgl.Companion_$external_object_getInstance") {
        object_result(ObjectKind::WebGlClass);
    } else if (name == "org.jetbrains.skiko.skikoRequestAnimationFrame" ||
               name == "org.jetbrains.skiko.windowRequestAnimationFrame" ||
               name == "org.w3c.dom.requestAnimationFrame_$external_fun" ||
               name.find("requestAnimationFrame") != std::string::npos) {
        HostValue* cb_val = nullptr;
        for (size_t i = 0; i < nargs; ++i) {
            HostValue* val = GetHostValue(args[i]);
            if (val && val->callback) {
                cb_val = val;
                break;
            }
        }
        if (cb_val && cb_val->callback) {
            animation_callbacks_.push_back(cb_val->callback);
        }
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = next_animation_id_++;
        }
    } else if (name.find("cancelAnimationFrame") != std::string::npos) {
        // no-op
    } else if (name == "kotlin.wasm.internal.getJsTrue") {
        SetExternResult(&results[0], HostValue::BooleanValue(true));
    } else if (name == "kotlin.wasm.internal.getJsFalse") {
        SetExternResult(&results[0], HostValue::BooleanValue(false));
    } else if (name == "kotlin.wasm.internal.getJsEmptyString") {
        string_result("");
    } else if (name == "kotlin.wasm.internal.intToExternref") {
        SetExternResult(&results[0], HostValue::Number(args[0].of.i32));
    } else if (name == "kotlin.wasm.internal.externrefToInt") {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = host ? static_cast<int32_t>(host->number) : 0;
    } else if (name == "kotlin.wasm.internal.externrefToDouble") {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        results[0].kind = WASMTIME_F64;
        results[0].of.f64 = host ? host->number : 0.0;
    } else if (name == "kotlin.wasm.internal.externrefToString") {
        string_result(GetString(args[0]));
    } else if (name == "kotlin.wasm.internal.itoa32_$external_fun" ||
               name == "kotlin.wasm.internal.utoa32_$external_fun") {
        string_result(std::to_string(args[0].of.i32));
    } else if (name == "kotlin.wasm.internal.itoa64_$external_fun" ||
               name == "kotlin.wasm.internal.utoa64_$external_fun") {
        string_result(std::to_string(args[0].of.i64));
    } else if (name == "kotlin.wasm.internal.newJsArray" ||
               name == "kotlin.js.JsArray_$external_fun") {
        object_result(ObjectKind::Array);
    } else if (name == "kotlin.wasm.internal.jsArrayPush") {
        // no-op
    } else if (name == "kotlin.js.jsArrayGet") {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (host && host->object == ObjectKind::Array && nargs > 1 && args[1].kind == WASMTIME_I32) {
            const size_t index = static_cast<size_t>(args[1].of.i32);
            if (index < host->string_array.size()) {
                string_result(host->string_array[index]);
            } else {
                null_result();
            }
        } else {
            null_result();
        }
    } else if (name == "kotlin.js.jsArraySet") {
        // no-op
    } else if (name == "androidx.navigation.refBrowserWindow") {
        object_result(ObjectKind::Window);
    } else if (name == "androidx.navigation.location_$external_prop_getter") {
        object_result(ObjectKind::Location);
    } else if (name == "androidx.navigation.history_$external_prop_getter") {
        object_result(ObjectKind::History);
    } else if (name == "androidx.navigation.origin_$external_prop_getter") {
        string_result("http://localhost");
    } else if (name == "androidx.navigation.pathname_$external_prop_getter") {
        string_result("/");
    } else if (name == "androidx.navigation.hash_$external_prop_getter") {
        string_result("");
    } else if (name == "androidx.navigation.state_$external_prop_getter" ||
               name == "androidx.navigation.state_$external_prop_getter_1") {
        null_result();
    } else if (name == "androidx.navigation.pushState_$external_fun" ||
               name == "androidx.navigation.replaceState_$external_fun") {
        // no-op
    } else if (name == "androidx.navigation.decodeURIComponent_$external_fun" ||
               name == "androidx.navigation.encodeURIComponent_$external_fun") {
        string_result(nargs > 0 ? GetString(args[0]) : "");
    } else if (name.find("cancelable_$external_prop_getter") != std::string::npos ||
               name.find("defaultPrevented_$external_prop_getter") != std::string::npos ||
               name.find("bubbles_$external_prop_getter") != std::string::npos ||
               name.find("composed_$external_prop_getter") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 0;
        }
    } else if (name.find("stopPropagation") != std::string::npos ||
               name.find("preventDefault") != std::string::npos ||
               name.find("stopImmediatePropagation") != std::string::npos) {
        // no-op
    } else if (name == "org.jetbrains.skiko.devicePixelRatio" ||
               name == "org.w3c.dom.devicePixelRatio_$external_prop_getter") {
        results[0].kind = WASMTIME_F64;
        results[0].of.f64 = 1.0;
    } else if (name == "org.w3c.dom.navigator_$external_prop_getter") {
        object_result(ObjectKind::Generic);
    } else if (name == "org.jetbrains.skiko.wasm.contextAttributesWithDefaults") {
        object_result(ObjectKind::ContextAttributes);
    } else if (name == "org.jetbrains.skia.impl.FinalizationRegistry_$external_fun") {
        object_result(ObjectKind::FinalizationRegistry);
    } else if (name == "org.jetbrains.skia.impl.unregister_$external_fun") {
        if (nresults) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 1;
        }
    } else if (name == "org.jetbrains.skia.navigatorLanguage" ||
               name == "org.jetbrains.skiko.getNavigatorInfo" ||
               name == "org.jetbrains.skiko.getNavigatorUserAgent" ||
               name == "org.w3c.dom.userAgent_$external_prop_getter" ||
               name == "org.w3c.dom.language_$external_prop_getter") {
        string_result(name.ends_with("navigatorLanguage") || name.ends_with("language_$external_prop_getter") ? "en-US" : "macOS");
    } else if (name == "org.w3c.dom.languages_$external_prop_getter" ||
               name.find("languages_$external_prop_getter") != std::string::npos ||
               name.find("languages") != std::string::npos) {
        SetExternResult(&results[0], HostValue::StringArray({"en-US", "en"}));
    } else if (name.find("length_$external_prop_getter") != std::string::npos) {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        results[0].kind = WASMTIME_I32;
        if (host && host->object == ObjectKind::Array) {
            results[0].of.i32 = static_cast<int32_t>(host->string_array.size());
        } else {
            results[0].of.i32 = 0;
        }
    } else if (name.find("get_$external_fun") != std::string::npos) {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (host && host->object == ObjectKind::Array && nargs > 1 && args[1].kind == WASMTIME_I32) {
            const size_t index = static_cast<size_t>(args[1].of.i32);
            if (index < host->string_array.size()) {
                string_result(host->string_array[index]);
            } else {
                null_result();
            }
        } else {
            null_result();
        }
    } else if (name.find("set_$external_fun") != std::string::npos) {
        if (nresults > 0) null_result();
    } else if (name.find("has_$external_fun") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 0;
        }
    } else if (name.find("delete_$external_fun") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 0;
        }
    } else if (name == "org.w3c.fetch.fetch_$external_fun") {
        object_result(ObjectKind::Response);
    } else if (name == "org.w3c.fetch.Response_$external_class_instanceof") {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 1;
    } else if (name == "org.w3c.fetch.arrayBuffer_$external_fun") {
        object_result(ObjectKind::Promise);
    } else if (name == "kotlin.wasm.internal.getJsClassName") {
        string_result("Object");
    } else if (name == "kotlin.wasm.internal.getConstructor") {
        object_result(ObjectKind::Generic);
    } else if (name == "kotlin.createJsError") {
        auto* error = HostValue::Object(ObjectKind::Error);
        error->string = nargs == 0 ? "Kotlin error" : GetString(args[0]);
        if (std::getenv("SKIKO_WASMTIME_TRACE") != nullptr) {
            std::cerr << "[createJsError] message=" << error->string << '\n';
        }
        SetExternResult(&results[0], error);
    } else if (name == "kotlin.js.JsError_$external_class_instanceof") {
        HostValue* value = GetHostValue(args[0]);
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = value && value->object == ObjectKind::Error;
    } else if (name == "kotlin.js.message_$external_prop_getter") {
        string_result(GetString(args[0]));
    } else if (name == "kotlin.js.kotlinException_$external_prop_getter") {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (host && host->has_kotlin_exception) {
            wasmtime_val_clone(&host->kotlin_exception, &results[0]);
        } else {
            null_result();
        }
    } else if (name == "kotlin.js.kotlinException_$external_prop_setter") {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (host && nargs > 1) {
            if (host->has_kotlin_exception) {
                wasmtime_val_unroot(&host->kotlin_exception);
                host->has_kotlin_exception = false;
            }
            wasmtime_val_clone(&args[1], &host->kotlin_exception);
            host->has_kotlin_exception = true;
        }
    } else if (name == "kotlin.wasm.internal.jsThrow" ||
               name == "kotlin.js.jsThrow" ||
               name == "org.jetbrains.skiko.jsThrow") {
        HostValue* host = (nargs > 0) ? GetHostValue(args[0]) : nullptr;
        if (host != nullptr) {
            last_thrown_exception_ = host;
        } else if (nargs > 0) {
            auto* err = HostValue::Object(ObjectKind::Error);
            wasmtime_val_clone(&args[0], &err->kotlin_exception);
            err->has_kotlin_exception = true;
            last_thrown_exception_ = err;
        }
        return nullptr;
    } else if (name.find("WeakRef") != std::string::npos && name.find("deref") == std::string::npos) {
        if (nargs > 0) {
            SetExternResult(&results[0], HostValue::WeakRefValue(args[0]));
        } else {
            object_result(ObjectKind::Generic);
        }
    } else if (name.find("deref") != std::string::npos) {
        HostValue* host = nargs > 0 ? GetHostValue(args[0]) : nullptr;
        if (host && host->has_target_val) {
            wasmtime_val_clone(&host->target_val, &results[0]);
        } else {
            null_result();
        }
    } else if (name.find("FinalizationRegistry") != std::string::npos) {
        object_result(ObjectKind::FinalizationRegistry);
    } else if (name.find("unregister") != std::string::npos) {
        if (nresults > 0) {
            results[0].kind = WASMTIME_I32;
            results[0].of.i32 = 1;
        }
    } else if (name.find("parseLanguageTagToIntlLocale") != std::string::npos) {
        object_result(ObjectKind::Generic);
    } else if (name.find("_baseName_$external_prop_getter") != std::string::npos) {
        string_result("en-US");
    } else if (name.find("toLocaleUpperCase") != std::string::npos) {
        string_result(nargs > 0 ? GetString(args[0]) : "");
    } else if (name.find("isSecureContext") != std::string::npos) {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 1;
    } else if (name.find("isSchedulerApiSupported") != std::string::npos ||
               name.find("isIdleApiSupported") != std::string::npos ||
               name.find("isFullWakeLockApiSupported") != std::string::npos ||
               name.find("isVibrationSupported") != std::string::npos ||
               name.find("hasVirtualKeyboard") != std::string::npos ||
               name.find("isClipboardReadSupported") != std::string::npos ||
               name.find("isClipboardWriteSupported") != std::string::npos ||
               name.find("isFallbackWriteTextApiAvailable") != std::string::npos ||
               name.find("isFullClipboardApiSupported") != std::string::npos ||
               name.find("webWakeLockSupported") != std::string::npos ||
               name.find("isInTestEnvironment") != std::string::npos ||
               name.find("supportsCacheApi") != std::string::npos ||
               name.find("isMouseEvent") != std::string::npos ||
               name.find("isTouchEvent") != std::string::npos ||
               name.find("isTypedEvent") != std::string::npos ||
               name.find("documentIsVisible") != std::string::npos ||
               name.find("instanceof") != std::string::npos ||
               name.find("Instanceof") != std::string::npos) {
        results[0].kind = WASMTIME_I32;
        results[0].of.i32 = 0;
    } else if (name == "org.jetbrains.skiko.wasm.patchWebGlContext" ||
               name.find("register_$external_fun") != std::string::npos ||
               name.find("unregister_$external_fun") != std::string::npos ||
               name == "org.w3c.dom.addEventListener_$external_fun" ||
               name == "org.w3c.dom.removeEventListener_$external_fun" ||
               name == "org.w3c.dom.setAttribute_$external_fun" ||
               name == "org.w3c.dom.focus_$external_fun" ||
               name == "org.w3c.dom.blur_$external_fun" ||
               name == "kotlin.js.name_$external_prop_setter") {
        // Deliberate no-op host shims.
    } else {
        if (nresults > 0) {
            if (name.find("instanceof") != std::string::npos ||
                name.find("Instanceof") != std::string::npos ||
                name.find("matches_$external_fun") != std::string::npos ||
                name.find("contains_$external_fun") != std::string::npos ||
                name.find("has_$external_fun") != std::string::npos ||
                name.find("delete_$external_fun") != std::string::npos) {
                results[0].kind = WASMTIME_I32;
                results[0].of.i32 = 0;
            } else {
                object_result(ObjectKind::Generic);
            }
        }
    }
    return nullptr;
}
