package treepeater;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;

/**
 * Stands in for the object factory Burp installs at runtime. The static factory methods on the Montoya
 * message interfaces all delegate to it, so without one any code that builds a real {@link HttpRequest}
 * fails outside Burp. Requests are parsed just far enough to answer the accessors the importer reads.
 *
 * <p>Built from {@link Proxy} rather than mocks: these objects are created while the factory call is
 * being answered, and creating mocks at that point corrupts Mockito's stubbing state.
 */
public final class MontoyaFactoryStub {

    private MontoyaFactoryStub() {}

    public static void install() {
        Map<Object, String> sources = new IdentityHashMap<>();
        ObjectFactoryLocator.FACTORY =
                proxy(MontoyaObjectFactory.class, (method, args) -> create(sources, method, args));
    }

    public static void uninstall() {
        ObjectFactoryLocator.FACTORY = null;
    }

    private static Object create(Map<Object, String> sources, Method method, Object[] args) {
        return switch (method.getName()) {
            case "byteArray" -> byteArray(sources, args[0]);
            case "httpService" -> args.length == 3 ? httpService(args) : null;
            case "httpRequest" -> args.length == 2 && args[1] instanceof ByteArray body
                    ? request(sources, (HttpService) args[0], body)
                    : null;
            case "httpResponse" -> args.length == 1 ? response(sources, args[0]) : null;
            case "httpRequestResponse" -> requestResponse(args);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ objects

    private static ByteArray byteArray(Map<Object, String> sources, Object source) {
        String text = source instanceof byte[] raw
                ? new String(raw, StandardCharsets.UTF_8)
                : String.valueOf(source);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArray array = fixed(ByteArray.class, Map.of("getBytes", bytes, "length", bytes.length));
        sources.put(array, text);
        return array;
    }

    private static HttpService httpService(Object[] args) {
        return fixed(
                HttpService.class, Map.of("host", args[0], "port", args[1], "secure", args[2]));
    }

    /** A request whose method, path and url come from the raw request line. */
    private static HttpRequest request(Map<Object, String> sources, HttpService service, ByteArray body) {
        String raw = sources.getOrDefault(body, "");
        String[] parts = raw.split("\r\n|\n", 2)[0].split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String target = parts.length > 1 ? parts[1] : "/";
        int query = target.indexOf('?');

        Map<String, Object> answers = new HashMap<>();
        answers.put("method", method);
        answers.put("path", target);
        answers.put("pathWithoutQuery", query >= 0 ? target.substring(0, query) : target);
        answers.put("url", url(service, target));
        answers.put("httpService", service);
        answers.put("toByteArray", body);
        return fixed(HttpRequest.class, answers);
    }

    private static HttpResponse response(Map<Object, String> sources, Object body) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("toByteArray", body);
        answers.put("statusCode", statusCode(sources.getOrDefault(body, "")));
        return fixed(HttpResponse.class, answers);
    }

    private static HttpRequestResponse requestResponse(Object[] args) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("request", args[0]);
        answers.put("response", args[1]);
        return fixed(HttpRequestResponse.class, answers);
    }

    private static short statusCode(String raw) {
        String[] parts = raw.split("\r\n|\n", 2)[0].split(" ");
        try {
            return parts.length > 1 ? Short.parseShort(parts[1]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String url(HttpService service, String target) {
        if (service == null) {
            return target;
        }
        boolean secure = service.secure();
        int port = service.port();
        String authority = port == (secure ? 443 : 80) ? service.host() : service.host() + ":" + port;
        return (secure ? "https://" : "http://") + authority + target;
    }

    // -------------------------------------------------------------------- proxy

    /** Interface implementation answering the named methods and returning defaults for the rest. */
    private static <T> T fixed(Class<T> type, Map<String, Object> answers) {
        return proxy(type, (method, args) -> answers.get(method.getName()));
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        InvocationHandler handler = (self, method, args) -> switch (method.getName()) {
            case "toString" -> type.getSimpleName() + "Stub";
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> {
                Object result = answer.answer(method, args != null ? args : new Object[0]);
                yield result != null ? result : defaultValue(method.getReturnType());
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0d;
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(Method method, Object[] args);
    }
}
