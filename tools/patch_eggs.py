import io

def patch(path, pairs):
    s = io.open(path, encoding="utf-8").read()
    for old, new, name in pairs:
        if old not in s:
            print("MISS:", name)
            continue
        s = s.replace(old, new, 1)
        print("OK:", name)
    io.open(path, "w", encoding="utf-8", newline="").write(s)

recent = r"D:\work_zone\pawviewer\app\src\main\java\com\pawviewer\ui\RecentFragment.kt"
patch(recent, [
    ("                filter = next\n                adapter.submitList(filter.apply(items))\n            }",
     "                filter = next\n                adapter.submitList(filter.apply(items))\n                updateEmptyState()\n            }",
     "filter-cb"),
    ("""                adapter.submitList(filter.apply(items))
                if (items.isEmpty()) {
                    if (end) binding.state.showEmpty(getString(R.string.empty)) else binding.state.hideState()
                } else {
                    binding.state.hideState()
                }""",
     """                adapter.submitList(filter.apply(items))
                updateEmptyState()""",
     "loadmore"),
    ("    private fun reload() {",
     """    /** 空列表时的文案：按时间筛选出的空白给彩蛋。 */
    private fun updateEmptyState() {
        val b = _binding ?: return
        if (items.isEmpty()) {
            if (end) b.state.showEmpty(getString(R.string.empty)) else b.state.hideState()
            return
        }
        if (filter.apply(items).isNotEmpty()) {
            b.state.hideState()
            return
        }
        when (filter.dateMode) {
            com.pawviewer.core.PostFilter.DATE_AFTER ->
                b.state.showEmptyBrand(getString(R.string.egg_future))

            com.pawviewer.core.PostFilter.DATE_BEFORE ->
                b.state.showEmptyBrand(getString(R.string.egg_past))

            else -> b.state.showEmpty(getString(R.string.empty))
        }
    }

    private fun reload() {""",
     "helper"),
])

creator = r"D:\work_zone\pawviewer\app\src\main\java\com\pawviewer\ui\CreatorFragment.kt"
patch(creator, [
    ("                filter = next\n                adapter.submitList(filter.apply(items))\n            }",
     "                filter = next\n                adapter.submitList(filter.apply(items))\n                updateEmptyState()\n            }",
     "filter-cb"),
    ("""                adapter.submitList(filter.apply(items))
                if (items.isEmpty() && end) {
                    binding.state.showEmpty(getString(R.string.empty))
                } else {
                    binding.state.hideState()
                }""",
     """                adapter.submitList(filter.apply(items))
                updateEmptyState()""",
     "loadmore"),
    ("    private fun reload() {",
     """    /** 空列表时的文案：按时间筛选出的空白给彩蛋。 */
    private fun updateEmptyState() {
        val b = _binding ?: return
        if (items.isEmpty()) {
            if (end) b.state.showEmpty(getString(R.string.empty)) else b.state.hideState()
            return
        }
        if (filter.apply(items).isNotEmpty()) {
            b.state.hideState()
            return
        }
        when (filter.dateMode) {
            com.pawviewer.core.PostFilter.DATE_AFTER ->
                b.state.showEmptyBrand(getString(R.string.egg_future))

            com.pawviewer.core.PostFilter.DATE_BEFORE ->
                b.state.showEmptyBrand(getString(R.string.egg_past))

            else -> b.state.showEmpty(getString(R.string.empty))
        }
    }

    private fun reload() {""",
     "helper"),
])
print("done")
