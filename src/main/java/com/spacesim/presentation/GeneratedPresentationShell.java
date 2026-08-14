package com.spacesim.presentation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks GPU/window integration shells whose behavior is covered through headless read models. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GeneratedPresentationShell {
}
