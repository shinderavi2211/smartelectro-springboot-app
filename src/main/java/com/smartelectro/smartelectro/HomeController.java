package com.smartelectro.smartelectro;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping({"/", "/cart", "/checkout", "/orders", "/wishlist",
            "/orders/{orderNumber}", "/order-confirmation/{orderNumber}", "/product/{id}", "/admin"})
    public String home() {
        return "forward:/index.html";
    }
}