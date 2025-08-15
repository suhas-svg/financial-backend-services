package com.suhasan.finance.transaction_service.health;

import java.util.Map;
import java.util.HashMap;

/**
 * Simple health status representation
 */
public class Health {
    
    private Status status;
    private Map<String, Object> details;
    
    public Health(Status status, Map<String, Object> details) {
        this.status = status;
        this.details = details != null ? details : new HashMap<>();
    }
    
    public static Health up() {
        return new Health(Status.UP, new HashMap<>());
    }
    
    public static Health down() {
        return new Health(Status.DOWN, new HashMap<>());
    }
    
    public Health withDetail(String key, Object value) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }
        this.details.put(key, value);
        return this;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public enum Status {
        UP("UP"),
        DOWN("DOWN"),
        OUT_OF_SERVICE("OUT_OF_SERVICE"),
        UNKNOWN("UNKNOWN");
        
        private final String code;
        
        Status(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
    }
}