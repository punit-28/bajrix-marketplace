package com.bajrix.marketplace.security;

import com.bajrix.marketplace.common.ApiException;
import com.bajrix.marketplace.seller.SellerRepository;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * MOCKED authentication: the caller states who they are with the {@code X-Seller-Id} header.
 * Everything downstream only relies on the resulting {@link SellerContext}, so replacing this class with
 * real JWT/session authentication would not touch controllers or services.
 */
@Component
public class SellerContextArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Seller-Id";

    private final SellerRepository sellers;

    public SellerContextArgumentResolver(SellerRepository sellers) {
        this.sellers = sellers;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentSeller.class)
                && SellerContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            throw ApiException.unauthorized("MISSING_SELLER", "Choose a seller (" + HEADER + " header is required).");
        }
        long id;
        try {
            id = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw ApiException.unauthorized("UNKNOWN_SELLER", "Unknown seller.");
        }
        if (!sellers.existsById(id)) {
            throw ApiException.unauthorized("UNKNOWN_SELLER", "Unknown seller.");
        }
        return new SellerContext(id);
    }
}
