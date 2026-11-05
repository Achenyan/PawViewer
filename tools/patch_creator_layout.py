import io

p = r"D:\work_zone\pawviewer\app\src\main\res\layout\fragment_creator.xml"
s = io.open(p, encoding="utf-8").read()

if "btn_filter" not in s:
    marker = '        <com.google.android.material.button.MaterialButton\n            android:id="@+id/fav_button"'
    insert = (
        '        <ImageButton\n'
        '            android:id="@+id/btn_filter"\n'
        '            android:layout_width="44dp"\n'
        '            android:layout_height="44dp"\n'
        '            android:layout_marginEnd="4dp"\n'
        '            android:background="?attr/selectableItemBackgroundBorderless"\n'
        '            android:contentDescription="@string/filter_button"\n'
        '            android:src="@drawable/ic_filter" />\n\n'
        + marker
    )
    if marker in s:
        s = s.replace(marker, insert, 1)
        io.open(p, "w", encoding="utf-8", newline="").write(s)
        print("inserted")
    else:
        print("MARKER NOT FOUND")
else:
    print("already present")

import xml.etree.ElementTree as ET
ET.parse(p)
print("creator xml ok")
