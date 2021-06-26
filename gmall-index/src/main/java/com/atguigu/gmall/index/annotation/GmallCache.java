package com.atguigu.gmall.index.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {


    //Cache key prefix
    String prefix() default "";

    //Cache expire time base on minute
    int timeout() default 5;

    //random value
    int random() default 5;
}
