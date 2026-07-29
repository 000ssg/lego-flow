package ssg.legoflow.upnp.demo.mcc;

import ssg.legoflow.upnp.mediaserver.ContentItem;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/**
 * A custom {@link Transferable} that carries a {@link ContentItem} for drag-and-drop
 * operations within the Media Control Center.
 *
 * <p>Supports a single custom {@link DataFlavor} representing a UPnP content item.
 * This enables dragging content items from the content browser table to the
 * now-playing panel (remote renderer), local playback panel, or between them
 * for synchronized playback.
 *
 * @since 1.0.0
 */
public class ContentItemTransferable implements Transferable {

    /**
     * The data flavor for transferring {@link ContentItem} instances via drag-and-drop.
     *
     * @since 1.0.0
     */
    public static final DataFlavor CONTENT_ITEM_FLAVOR = new DataFlavor(
            ContentItem.class, "UPnP Content Item");

    private static final DataFlavor[] SUPPORTED_FLAVORS = {CONTENT_ITEM_FLAVOR};

    private final ContentItem item;

    /**
     * Creates a new transferable wrapping the given content item.
     *
     * @param item the content item to transfer
     * @throws NullPointerException if {@code item} is null
     * @since 1.0.0
     */
    public ContentItemTransferable(ContentItem item) {
        if (item == null) {
            throw new NullPointerException("item must not be null");
        }
        this.item = item;
    }

    /**
     * Returns the supported data flavors. Only {@link #CONTENT_ITEM_FLAVOR} is supported.
     *
     * @return an array containing the content item data flavor
     * @since 1.0.0
     */
    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return SUPPORTED_FLAVORS.clone();
    }

    /**
     * Returns whether the given data flavor is supported.
     *
     * @param flavor the data flavor to check
     * @return {@code true} if the flavor is {@link #CONTENT_ITEM_FLAVOR}
     * @since 1.0.0
     */
    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return CONTENT_ITEM_FLAVOR.equals(flavor);
    }

    /**
     * Returns the transfer data for the given flavor.
     *
     * @param flavor the requested data flavor
     * @return the {@link ContentItem} if the flavor matches
     * @throws UnsupportedFlavorException if the flavor is not {@link #CONTENT_ITEM_FLAVOR}
     * @since 1.0.0
     */
    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!CONTENT_ITEM_FLAVOR.equals(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return item;
    }
}
