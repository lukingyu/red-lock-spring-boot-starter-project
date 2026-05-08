package com.github.lukingyu.redlock.sample.service;

import com.github.lukingyu.redlock.autoconfigure.annotation.Idempotent;
import com.github.lukingyu.redlock.sample.entity.dto.OrderDTO;
import org.springframework.stereotype.Service;

@Service
public class SampleOrderService {

    public String submit(OrderDTO order) {
        return "order submitted: " + order.getOrderNo();
    }

    @Idempotent(key = "#messageId", timeout = 30)
    public void handleMessage(String messageId) {
        // Use explicit business keys in scheduled jobs or message consumers.
    }
}
