package com.github.lukingyu.redlock.sample.controller;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.sample.entity.dto.OrderDTO;
import com.github.lukingyu.redlock.sample.service.SampleOrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class SampleOrderController {

    private final SampleOrderService orderService;

    public SampleOrderController(SampleOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/submit")
    @Idempotent(timeout = 5, message = "请勿重复提交订单")
    public String submit(@RequestBody OrderDTO order) {
        return orderService.submit(order);
    }

    @PostMapping("/submit-by-business-key")
    @Idempotent(key = "#order.userId + ':' + #order.orderNo", timeout = 10, message = "该订单正在处理中")
    public String submitByBusinessKey(@RequestBody OrderDTO order) {
        return orderService.submit(order);
    }
}
