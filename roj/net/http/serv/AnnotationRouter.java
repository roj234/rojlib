package roj.net.http.serv;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * @author solo6975
 * @since 2022/3/27 14:26
 */
public class AnnotationRouter implements Router {
    public AnnotationRouter(Object o) {
        for(Method m : (o instanceof Class ? (Class<?>)o : o.getClass()).getDeclaredMethods()) {
            Route r = m.getAnnotation(Route.class);
            if (r != null) {
                String v = r.value();
            }
        }
    }

    @Override
    public Response response(Request req, RequestHandler rh) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Route {
        String value();
    }
}
