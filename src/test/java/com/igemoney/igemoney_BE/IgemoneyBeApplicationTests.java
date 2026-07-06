package com.igemoney.igemoney_BE;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.sql.init.mode=never",
	"vector.enabled=false"
})
class IgemoneyBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
