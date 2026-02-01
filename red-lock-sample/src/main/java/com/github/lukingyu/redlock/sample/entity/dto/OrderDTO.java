package com.github.lukingyu.redlock.sample.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderDTO {

    private String userId;

    private List<String> skuIds;

}
