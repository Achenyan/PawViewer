import io, sys

def patch(path, pairs):
    s = io.open(path, encoding="utf-8").read()
    for old, new, name in pairs:
        if old not in s:
            print("MISS:", name)
            continue
        s = s.replace(old, new, 1)
        print("OK:", name)
    io.open(path, "w", encoding="utf-8", newline="").write(s)

# ---------------- RecentFragment ----------------
recent = r"D:\work_zone\pawviewer\app\src\main\java\com\pawviewer\ui\RecentFragment.kt"
patch(recent, [
    ("import com.pawviewer.core.Prefs",
     "import com.pawviewer.core.PostFilter\nimport com.pawviewer.core.Prefs",
     "import"),
    ("    private var service = \"\"",
     "    private var service = \"\"\n    private var filter = PostFilter()",
     "field"),
    ("        buildChips()\n        reload()",
     "        buildChips()\n        binding.btnFilter.setOnClickListener {\n"
     "            FilterDialog.show(requireContext(), filter) { next ->\n"
     "                filter = next\n"
     "                adapter.submitList(filter.apply(items))\n"
     "            }\n"
     "        }\n        reload()",
     "button"),
    ("                adapter.submitList(items.toList())",
     "                adapter.submitList(filter.apply(items))",
     "apply1"),
])

# ---------------- CreatorFragment ----------------
creator = r"D:\work_zone\pawviewer\app\src\main\java\com\pawviewer\ui\CreatorFragment.kt"
patch(creator, [
    ("import com.pawviewer.core.Prefs",
     "import com.pawviewer.core.PostFilter\nimport com.pawviewer.core.Prefs",
     "import"),
    ("    private var selectedTag = \"\"",
     "    private var selectedTag = \"\"\n    private var filter = PostFilter()",
     "field"),
    ("        binding.favButton.setOnClickListener { toggleFavorite() }",
     "        binding.favButton.setOnClickListener { toggleFavorite() }\n\n"
     "        binding.btnFilter.setOnClickListener {\n"
     "            FilterDialog.show(requireContext(), filter) { next ->\n"
     "                filter = next\n"
     "                adapter.submitList(filter.apply(items))\n"
     "            }\n"
     "        }",
     "button"),
    ("                adapter.submitList(items.toList())",
     "                adapter.submitList(filter.apply(items))",
     "apply1"),
])
print("done")
