package cc.rpc.core.consumer;

import cc.rpc.core.annotation.CcConsumer;
import cc.rpc.core.api.LoadBalancer;
import cc.rpc.core.api.RegisterCenter;
import cc.rpc.core.api.Router;
import cc.rpc.core.api.RpcContext;
import cc.rpc.core.meta.InstanceMeta;
import cc.rpc.core.meta.ServiceMeta;
import cc.rpc.core.registry.ChangedListener;
import cc.rpc.core.registry.Event;
import cc.rpc.core.util.MethodUtil;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author nhsoft.lsd
 */
@Slf4j
public class ConsumerBootstrap implements ApplicationContextAware {

    private Map<String, Object> stub = new HashMap<>();

    private ApplicationContext applicationContext;

    private RegisterCenter rc;

    @Value("${app.id}")
    private String app;

    @Value("${app.namespace}")
    private String namespace;

    @Value("${app.env}")
    private String env;

    @Override
    public void setApplicationContext(@NotNull final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PreDestroy
    public void stop() {
        log.info("consumer bootstrap stopped");
        rc.stop();
    }

    public void start() {

        log.info("consumer bootstrap starting");

        LoadBalancer loadBalancer = applicationContext.getBean(LoadBalancer.class);
        Router router = applicationContext.getBean(Router.class);
        rc = applicationContext.getBean(RegisterCenter.class);
        rc.start();

        RpcContext context = new RpcContext();
        context.setRouter(router);
        context.setLoadBalancer(loadBalancer);

        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {

            Object bean = applicationContext.getBean(beanName);

            Class clazz = bean.getClass();

            List<Field> fields =  MethodUtil.findAnnotatedField(clazz, CcConsumer.class);

            fields.forEach(field -> {
                Class<?> service = field.getType();
                String serviceName = service.getCanonicalName();

                Object proxy = stub.get(serviceName);

                if (proxy == null) {
                    proxy = createConsumerFromRegister(service, context, rc);
                    stub.put(serviceName, proxy);
                }
                try {
                    field.setAccessible(true);
                    field.set(bean, proxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private Object createConsumerFromRegister(Class<?> service, RpcContext context, RegisterCenter rc) {
        ServiceMeta serviceMeta = ServiceMeta.builder().app(app).namespace(namespace).env(env).service(service.getCanonicalName()).build();
        List<InstanceMeta> providers = rc.fetchAll(serviceMeta);
        Object proxy = createConsumer(service, context, providers);
        rc.subscribe(serviceMeta, event -> {
            providers.clear();
            providers.addAll(event.getData());
        });
        return proxy;
    }

    private Object createConsumer(Class<?> service, RpcContext context, List<InstanceMeta> providers) {
        return Proxy.newProxyInstance(service.getClassLoader(), new Class[]{service}, new CcRpcInvocationHandler(service, context, providers));
    }
}
