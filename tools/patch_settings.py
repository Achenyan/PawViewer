import io

# ---- layout ----
p = r"D:\work_zone\pawviewer\app\src\main\res\layout\fragment_settings.xml"
s = io.open(p, encoding="utf-8").read()
if "btn_clean_orphan" not in s:
    marker = '        <com.google.android.material.button.MaterialButton\n            android:id="@+id/btn_reset"'
    insert = (
        '        <com.google.android.material.button.MaterialButton\n'
        '            android:id="@+id/btn_clean_orphan"\n'
        '            style="@style/Widget.Material3.Button.TonalButton"\n'
        '            android:layout_width="wrap_content"\n'
        '            android:layout_height="wrap_content"\n'
        '            android:layout_marginTop="8dp"\n'
        '            android:text="@string/settings_clean_orphan" />\n\n' + marker
    )
    print("layout marker:", marker in s)
    s = s.replace(marker, insert, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
import xml.etree.ElementTree as ET
ET.parse(p)
print("settings xml ok")

# ---- fragment code ----
f = r"D:\work_zone\pawviewer\app\src\main\java\com\pawviewer\ui\SettingsFragment.kt"
c = io.open(f, encoding="utf-8").read()
if "btnCleanOrphan" not in c:
    old = "        binding.btnReset.setOnClickListener {"
    new = (
        '        binding.btnCleanOrphan.setOnClickListener {\n'
        '            val removed = with(requireContext()) {\n'
        '                val root = getExternalFilesDir("downloads") ?: filesDir\n'
        '                val known = com.pawviewer.core.db.Store.downloads(this).map { it.dir }.toSet()\n'
        '                var count = 0\n'
        '                root.listFiles()?.forEach { serviceDir ->\n'
        '                    serviceDir.listFiles()?.forEach { postDir ->\n'
        '                        if (!known.contains(postDir.absolutePath)) {\n'
        '                            if (postDir.deleteRecursively()) count++\n'
        '                        }\n'
        '                    }\n'
        '                    if (serviceDir.listFiles().isNullOrEmpty()) serviceDir.delete()\n'
        '                }\n'
        '                count\n'
        '            }\n'
        '            Util.toast(requireContext(), getString(R.string.settings_clean_orphan_done, removed))\n'
        '        }\n\n' + old
    )
    print("code marker:", old in c)
    c = c.replace(old, new, 1)
    io.open(f, "w", encoding="utf-8", newline="").write(c)
print("settings code done")
