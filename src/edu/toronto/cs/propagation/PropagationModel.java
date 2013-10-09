package edu.toronto.cs.propagation;

import java.util.Set;

import edu.toronto.cs.propagation.util.Node;

/**
 * An abstract propagation model.
 *
 */
public abstract class PropagationModel {
	protected SocialNetwork sn;
	
	public SocialNetwork getSn() {
		return sn;
	}

	protected PropagationModel( SocialNetwork sn ) {
		this.sn = sn;
	}
	
	public PropagationModel() {
		throw new IllegalArgumentException("Not initialized correctly, you need to override this constructor");
	}
	
	/**
	 * Runs the propagation of an action from a starting node.
	 * 
	 * @param startNode the starting node
	 * @return the history of the propagation of this action
	 */
	protected abstract PropagationHistory runModel( Node startNode );
	
	/**
	 * Runs the propagation of an action from a set of starting nodes.
	 * 
	 * @param startNodes the starting nodes
	 * @return the history of the propagation of this action
	 */
	protected abstract PropagationHistory runModel( Set<Node> startNodes );
}
