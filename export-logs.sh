#!/system/bin/sh
umask 077
last_folders=
while true; do
    folders=
    for folder in /storage/usb[012]/TBT-LOGS; do
        [ -d "$folder" ] || continue
        folders="$folders $folder"
    done
    for folder in $folders; do
        case "$folder" in /storage/usb[012]/TBT-LOGS) ;; *) continue ;; esac
        case " $last_folders " in *" $folder "*) continue ;; esac

        tmp="$folder/.wired-carplay-tbt.log.part"
        log="$folder/wired-carplay-tbt.log"
        if {
            echo "build=$(getprop ro.build.product)"
            echo "captured=$(date)"
            echo "### HMG-API mod log"
            if saved_log=$(hmgapid log); then
                echo "$saved_log" | grep -e '\[wired-carplay-tbt-lx2pe\]' -e 'cluster: .*failed' -e 'cluster: no NavigationCluster' ||
                    echo 'No matching mod lines in the HMG-API log.'
            else
                echo 'Could not read the HMG-API log.'
            fi
            echo "### Extended turn history (older file first)"
            for history in /data/hmgapi/logs/hmgapi/log.log.1 /data/hmgapi/logs/hmgapi/log.log; do
                [ -r "$history" ] || continue
                grep -F '[wired-carplay-tbt-lx2pe]' "$history" |
                    grep -E ' turn index=|cluster path no icon|guidance source=|cluster path clear sent' |
                    tail -n 2000
            done
            echo 'finished=1'
        } > "$tmp" && mv "$tmp" "$log"; then
            sync
            echo "Exported TBT logs to $log"
        else
            rm "$tmp" 2>/dev/null
            echo "Could not export TBT logs to $folder"
        fi
    done
    last_folders="$folders"
    sleep 2
done
