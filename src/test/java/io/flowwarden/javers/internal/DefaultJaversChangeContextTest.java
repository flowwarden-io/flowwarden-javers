package io.flowwarden.javers.internal;

import io.flowwarden.stream.ChangeStreamContext;
import org.bson.BsonDocument;
import org.javers.core.commit.CommitMetadata;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.core.metamodel.object.GlobalId;
import org.javers.core.metamodel.object.SnapshotType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultJaversChangeContextTest {

    @Test
    void delegatesJaversAccessorsToSnapshot() {
        CdoSnapshot snapshot = mock(CdoSnapshot.class);
        GlobalId globalId = mock(GlobalId.class);
        CommitMetadata commitMetadata = mock(CommitMetadata.class);
        ChangeStreamContext<?> delegate = mock(ChangeStreamContext.class);

        when(snapshot.getType()).thenReturn(SnapshotType.UPDATE);
        when(snapshot.getCommitMetadata()).thenReturn(commitMetadata);
        when(snapshot.getChanged()).thenReturn(List.of("name", "price"));
        when(snapshot.getVersion()).thenReturn(3L);
        when(snapshot.getGlobalId()).thenReturn(globalId);
        when(globalId.value()).thenReturn("io.flowwarden.javers.test.fixture.Product/p1");

        DefaultJaversChangeContext<Object> ctx = new DefaultJaversChangeContext<>(snapshot, delegate);

        assertThat(ctx.getSnapshot()).isSameAs(snapshot);
        assertThat(ctx.getSnapshotType()).isEqualTo(SnapshotType.UPDATE);
        assertThat(ctx.getCommitMetadata()).isSameAs(commitMetadata);
        assertThat(ctx.getChangedProperties()).containsExactly("name", "price");
        assertThat(ctx.getVersion()).isEqualTo(3L);
        assertThat(ctx.getEntityId()).isEqualTo("io.flowwarden.javers.test.fixture.Product/p1");
    }

    @Test
    void delegatesFlowWardenAccessorsToChangeStreamContext() {
        CdoSnapshot snapshot = mock(CdoSnapshot.class);
        GlobalId globalId = mock(GlobalId.class);
        when(snapshot.getGlobalId()).thenReturn(globalId);
        ChangeStreamContext<?> delegate = mock(ChangeStreamContext.class);

        BsonDocument resumeToken = new BsonDocument();
        Instant clusterTime = Instant.ofEpochSecond(1_700_000_000L);
        Map<String, Object> allMeta = Map.of("k", "v");

        when(delegate.getEventId()).thenReturn("evt-1");
        when(delegate.getClusterTime()).thenReturn(clusterTime);
        when(delegate.getResumeToken()).thenReturn(resumeToken);
        when(delegate.getAllMetadata()).thenReturn(allMeta);
        when(delegate.getMetadata("k", String.class)).thenReturn(Optional.of("v"));

        DefaultJaversChangeContext<Object> ctx = new DefaultJaversChangeContext<>(snapshot, delegate);

        assertThat(ctx.getEventId()).isEqualTo("evt-1");
        assertThat(ctx.getClusterTime()).isEqualTo(clusterTime);
        assertThat(ctx.getResumeToken()).isSameAs(resumeToken);
        assertThat(ctx.getAllMetadata()).isEqualTo(allMeta);
        assertThat(ctx.getMetadata("k", String.class)).contains("v");

        ctx.saveCheckpointNow();
        verify(delegate).saveCheckpointNow();

        ctx.sendToDlq("boom");
        verify(delegate).sendToDlq("boom");

        ctx.addMetadata("k2", 42);
        verify(delegate).addMetadata("k2", 42);
    }
}
