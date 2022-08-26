# TODO

## Replicating majority voting in software

As observed by us, the algorithm used for majority voting during software prediction is different from that used in hardware. The sklearn-based prediction method involves probability averaging to find the final classification while hardware utilizes a simple vote based majority counting strategy. This inturn leads to discrepancies between prediction in software and hardware. One way to overcome this would be to replicate majority voting in software, such that the hardware behaves like software. The following code snippet could be used to do this:

```scala
def predict_majorityvote(clf, X):
    from scipy.stats import mode

    all_preds = [e.predict(X) for e in clf.estimators_]
    modes, _ = mode(all_preds, axis=0)
    return modes[0].astype('int64')
```

## Add hints for maximum required range and precision based on dataset while building

- Currently chisel throws an error if a threshold (fixed point) in the ROM beyond the possible range of values representable with the user provided fixed point width. But this error occurs on the first encountered value that is beyond the range and an error could be thrown again if another such case is encountered. A possible enhancement would be to provide a hint to the user on what the minimum required fixed point width to represent the range is based on the data values.

- Also if a value requires a precision that is smaller than what is possible with the user provided fixed point width, currently there are warnings/hints displayed. The values silently get truncated during chisel to verilog compilation. A possible enhancement would be to show a hint to the user on the minimum required fixed point width to represent the smallest precision is based on the data values.
