package com.github.lukingyu.redlock.sample.controller;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.sample.entity.dto.OrderDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class SampleOrderController {

    @Autowired
            @Lazy
    SampleOrderController selfProxy;

    @PostMapping("/submit")
//    @Idempotent(timeout = 3, message = "请勿重复点击")
    public String submit(@RequestBody OrderDTO dto) {
        return selfProxy.noWeb("1234");
    }

    @Idempotent(spEL = "#userId")
    public String noWeb(String userId) {
        return "";
    }


}
