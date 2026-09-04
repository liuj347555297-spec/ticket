package cn.servicehub.designer;
import java.util.List;
import java.util.Optional;
import cn.servicehub.designer.StudioModels.Draft;
public interface StudioDraftRepository {
    List<Draft> list();
    Optional<Draft> find(String id);
    Draft insert(Draft value);
    Draft update(Draft value, long expectedVersion);
}
