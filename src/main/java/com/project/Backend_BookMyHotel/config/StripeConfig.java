package com.project.Backend_BookMyHotel.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

// The Stripe SDK reads its API key off this one static field rather than an injected client
// object — every PaymentIntent/Refund call implicitly uses whatever Stripe.apiKey was last set.
@Configuration
public class StripeConfig {

    @Value("${stripe.secret.key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}
