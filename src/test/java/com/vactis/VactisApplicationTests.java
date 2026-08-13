package com.vactis;

import com.vactis.config.DataFictifSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureTestDatabase
class VactisApplicationTests {

    @MockitoBean
    private DataFictifSeeder dataFictifSeeder;

    @Test
    void contextLoads() {
    }
}
