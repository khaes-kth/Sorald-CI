package com.purbon.kafka.topology.actions;

import com.purbon.kafka.topology.roles.TopologyAclBinding;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BaseAccessControlAction extends BaseAction {

  private static final Logger LOGGER = LogManager.getLogger(BaseAccessControlAction.class);

  protected Collection<TopologyAclBinding> bindings;

  public BaseAccessControlAction() {
    this(new ArrayList<>());
  }

  public BaseAccessControlAction(Collection<TopologyAclBinding> bindings) {
    this.bindings = bindings;
  }

  @Override
  public void run() throws IOException {
    LOGGER.debug(String.format("Running Action %s", getClass()));
    execute();
    if (!getBindings().isEmpty()) logResults();
  }

  private void logResults() {
    List<String> bindingsAsList =
        getBindings().stream()
            .filter(Objects::nonNull)
            .map(TopologyAclBinding::toString)
            .collect(Collectors.toList());
    LOGGER.debug(String.format("Bindings created %s", String.join("\n", bindingsAsList)));
  }

  protected abstract void execute() throws IOException;

  @Override
  public List<TopologyAclBinding> getBindings() {
    return new ArrayList<>(bindings);
  }
}
