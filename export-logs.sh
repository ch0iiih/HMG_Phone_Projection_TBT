#!/system/bin/sh
umask 077
history_dir=/data/hmgapi/logs/wired-tbt-capture
capture_pid=
if mkdir -p "$history_dir"; then
    logcat -v threadtime -f "$history_dir/capture.log" -r 2048 -n 5 -s HMG-API:I '*:S' &
    capture_pid=$!
fi
trap '[ -z "$capture_pid" ] || kill "$capture_pid" 2>/dev/null' EXIT
trap 'exit 0' HUP INT TERM
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
        capture_tmp="$folder/.stock-guidance.log.part"
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
                    grep -E ' turn index=|cluster path no icon|guidance source=|cluster path clear sent|Native display updateTBTInfo first=|Native display updateTPCrossroadName.*Text=true|Stock guidance |lane detail |lane frame |Native display updateLaneInfo.*frame=' |
                    tail -n 6000
            done
            echo 'finished=1'
        } > "$tmp" && mv "$tmp" "$log" && {
            echo "captured=$(date)"
            for history in "$history_dir"/capture.log.5 "$history_dir"/capture.log.4 \
                    "$history_dir"/capture.log.3 "$history_dir"/capture.log.2 \
                    "$history_dir"/capture.log.1 "$history_dir"/capture.log; do
                [ -r "$history" ] || continue
                grep -F '[wired-carplay-tbt-lx2pe]' "$history" || :
            done
            echo 'finished=1'
        } > "$capture_tmp" && mv "$capture_tmp" "$folder/stock-guidance.log"; then
            sync
            echo "Exported TBT logs to $log"
        else
            rm "$tmp" 2>/dev/null
            rm "$capture_tmp" 2>/dev/null
            echo "Could not export TBT logs to $folder"
        fi
    done
    last_folders="$folders"
    sleep 2
done
