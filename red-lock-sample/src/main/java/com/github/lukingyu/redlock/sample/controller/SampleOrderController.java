package com.github.lukingyu.redlock.sample.controller;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.sample.entity.dto.OrderDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class SampleOrderController {

    @PostMapping("/submit")
    @Idempotent(timeout = 3, message = "请勿重复点击")
    public String submit(@RequestBody OrderDTO dto) {
        return "";
    }


}
