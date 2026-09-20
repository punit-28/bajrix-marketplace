package com.bajrix.marketplace.security;

import java.lang.annotation.*;

/** Marks a controller parameter that must be filled with the authenticated seller. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentSeller {
}
