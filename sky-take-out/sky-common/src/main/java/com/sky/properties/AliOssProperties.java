package com.sky.properties;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.alioss")
@Data
public class AliOssProperties {

    private String endpoint;
    @ToString.Exclude
    private String accessKeyId;
    @ToString.Exclude
    private String accessKeySecret;
    private String bucketName;

}
