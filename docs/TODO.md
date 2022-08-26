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
