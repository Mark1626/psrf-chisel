package psrf
import config.{Config, Parameters}

class DefaultConfig
    extends Config((site, here, up) => {
      case NumFeatures           => 4
      case NumClasses            => 3
      case NumTrees              => 4
      case FixedPointWidth       => 8
      case FixedPointBinaryPoint => 4
      case TreeLiterals =>
        List(
          List(
            DecisionTreeNodeLit(false, 0, 5.299999952316284, 2, 1),
            DecisionTreeNodeLit(true, 0, -2.0, -1, -1),
            DecisionTreeNodeLit(false, 2, 4.950000047683716, 4, 3),
            DecisionTreeNodeLit(true, 1, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 2, -2.0, -1, -1)
          ),
          List(
            DecisionTreeNodeLit(false, 3, 1.449999988079071, 2, 1),
            DecisionTreeNodeLit(false, 3, 0.75, 4, 3),
            DecisionTreeNodeLit(true, 2, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 0, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 1, -2.0, -1, -1)
          ),
          List(
            DecisionTreeNodeLit(false, 3, 1.6500000357627869, 2, 1),
            DecisionTreeNodeLit(false, 3, 0.75, 4, 3),
            DecisionTreeNodeLit(true, 2, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 0, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 1, -2.0, -1, -1)
          ),
          List(
            DecisionTreeNodeLit(false, 2, 4.700000047683716, 2, 1),
            DecisionTreeNodeLit(false, 3, 0.75, 4, 3),
            DecisionTreeNodeLit(true, 2, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 0, -2.0, -1, -1),
            DecisionTreeNodeLit(true, 1, -2.0, -1, -1)
          )
        )
    })
