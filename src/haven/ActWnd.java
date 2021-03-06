package haven;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import haven.util.CollectionListener;

public abstract class ActWnd extends Window {
    private static final int WIDTH = 200;

    private final TextEntry entry;
    private final List<MenuGrid.Pagina> all = new ArrayList<MenuGrid.Pagina>();
    private final ActList list;
    private final String filter;
    private PaginaeListener listener;

    public ActWnd(String caption, String filter) {
        super(Coord.z, caption);
        this.filter = filter;
        setLocal(true);
        setHideOnClose(true);
        setcanfocus(true);
        setfocusctl(true);
        entry = add(new TextEntry(WIDTH, "") {
            @Override
            public void activate(String text) {
                act(list.sel.pagina);
                ActWnd.this.hide();
            }

            @Override
            protected void changed() {
                super.changed();
                refilter();
            }

            @Override
            public boolean keydown(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                    list.change(Math.max(list.selindex - 1, 0));
                    list.display();
                    return true;
                } else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                    list.change((Math.min(list.selindex + 1, list.listitems() - 1)));
                    list.display();
                    return true;
                } else {
                    return super.keydown(e) ;
                }
            }
        });
        setfocus(entry);
        list = add(new ActList(WIDTH, 10) {
            protected void itemactivate(ActItem item) {
                act(list.sel.pagina);
                ActWnd.this.hide();
            }
        }, 0, entry.sz.y + 5);
        pack();
    }

    protected abstract void act(MenuGrid.Pagina act);

    @Override
    public void show() {
        super.show();
        entry.settext("");
        list.change(0);
        list.display();
        parent.setfocus(this);
    }

    @Override
    public void lostfocus() {
        super.lostfocus();
        hide();
    }

    @Override
    public void tick(double dt) {
        if (ui == null)
            return;

        if (listener == null) {
            // make initial list
            all.clear();
            synchronized (ui.gui.menu.paginae) {
                for (MenuGrid.Pagina pagina : ui.gui.menu.paginae) {
                    if (isIncluded(pagina))
                        all.add(pagina);
                }
                listener = new PaginaeListener();
                ui.gui.menu.paginae.addListener(listener);
                refilter();
            }
        }
    }

    private void refilter() {
        list.clear();
        for (MenuGrid.Pagina p : all) {
            if (p.res.get().layer(Resource.action).name.toLowerCase().contains(entry.text.toLowerCase()))
                list.add(p);
        }
        list.sort(new ItemComparator());
        if (list.listitems() > 0) {
            list.change(Math.min(list.selindex, list.listitems() - 1));
            list.sb.val = 0;
            list.display();
        }
    }

    private class PaginaeListener implements CollectionListener<MenuGrid.Pagina> {
        @Override
        public void onItemAdded(MenuGrid.Pagina item) {
            if (isIncluded(item)) {
                all.add(item);
                refilter();
            }
        }

        @Override
        public void onItemRemoved(MenuGrid.Pagina item) {
            all.remove(item);
            refilter();
        }
    }

    private class ItemComparator implements Comparator<ActList.ActItem> {
        @Override
        public int compare(ActList.ActItem a, ActList.ActItem b) {
            return a.name.text.compareTo(b.name.text);
        }
    }

    private boolean isIncluded(MenuGrid.Pagina pagina) {
        Resource res = null;
        try {
            res = pagina.res();
        } catch (Loading e) {
            try {
                e.waitfor();
                res = pagina.res();
            } catch (InterruptedException ex) {}
        }
        return (res != null) && res.name.startsWith(filter);
    }
}