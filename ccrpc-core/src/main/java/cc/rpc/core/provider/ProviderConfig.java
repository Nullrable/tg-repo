package cc.rpc.core.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author nhsoft.lsd
 */
@Configuration
public class ProviderConfig {

    @Bean(destroyMethod = "stop")
    public ProviderBootstrap createProviderBootstrap() {
        return new ProviderBootstrap();
    }
}
