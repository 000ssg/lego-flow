package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DataProcessor;

@SuppressWarnings("unchecked")
public class BidirectionalPipe<A, B> {

    private final DataProcessor<A, B> left;
    private final DataProcessor<B, A> right;

    public BidirectionalPipe(DataProcessor<A, B> left, DataProcessor<B, A> right) {
        this.left = left;
        this.right = right;
    }

    public void sendLeftToRight(Context ctx, A... data) {
        left.consume(ctx, data);
    }

    public void sendRightToLeft(Context ctx, B... data) {
        right.consume(ctx, data);
    }

    public DataProcessor<A, B> getLeft() {
        return left;
    }

    public DataProcessor<B, A> getRight() {
        return right;
    }

    public void close() {
        left.close();
        right.close();
    }
}
