package cn.kungreat.boot.an;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CpdogController {
    int index() default 0;
}
