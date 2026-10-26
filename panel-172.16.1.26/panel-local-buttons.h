/* Local gesture-to-relay bindings. No HA/WLAN dependency during operation.
 * Values 0:none, 1:toggle physical relay 1, 2:toggle physical relay 2.
 * Local actions require a fresh hardware gesture and known MCU feedback.
 * Receiving/restoring a binding never operates a relay. */
static int local_bindings[4][2],local_report=1;
static int local_targets[2]={-1,-1};
static long long local_target_until[2];
static void local_module_disconnected(void){
    for(int i=0;i<2;i++){local_targets[i]=-1;local_target_until[i]=0;}
}
static void local_relay_feedback(int index,int value){if(local_targets[index]==value)local_target_until[index]=0;}
static int local_button_enabled(int button){return button>=1&&button<=4&&(local_bindings[button-1][0]||local_bindings[button-1][1]);}
static void local_disconnect_gestures(void){
    gesture_cancel(0);
    for(int b=1;b<=4;b++)if(local_button_enabled(b))local_suppress_ha[b]=1;else gesture_cancel(b);
}
static void local_button_action(int control,const char *event){
    if(control<1||control>4||dry_run)return;
    int gesture=!strcmp(event,"press")?0:!strcmp(event,"double_press")?1:-1;
    if(gesture<0)return;
    int relay=local_bindings[control-1][gesture];if(!relay||!module_online)return;
    int i=relay-1,value=module_values[i];
    if(value<0)return;
    if(local_target_until[i]>ms()&&local_targets[i]!=value)value=local_targets[i];
    int desired=!value;
    if(!module_relay_command(relay,desired)){
        local_targets[i]=desired;local_target_until[i]=ms()+5000;
        printf("Local button %d %s -> relay %d %s (%s).\n",control,event,relay,desired?"ON":"OFF",net<0?"MQTT offline":"MQTT online");
    }
}
static int local_save(int proposed[4][2]){
    FILE *f=fopen(STATE"/button-local-bindings.new","w");if(!f)return -1;
    for(int i=0;i<4;i++)fprintf(f,"%d %d\n",proposed[i][0],proposed[i][1]);
    int bad=fflush(f)||fsync(fileno(f));if(fclose(f))bad=1;
    if(bad||chmod(STATE"/button-local-bindings.new",0600)||rename(STATE"/button-local-bindings.new",STATE"/button-local-bindings"))return -1;
    return 0;
}
static void local_init(void){
    FILE *f=fopen(STATE"/button-local-bindings","r");if(!f)return;
    int proposed[4][2],ok=1;
    for(int i=0;i<4;i++)if(fscanf(f,"%d %d",&proposed[i][0],&proposed[i][1])!=2||proposed[i][0]<0||proposed[i][0]>2||proposed[i][1]<0||proposed[i][1]>2){ok=0;break;}
    fclose(f);if(ok)memcpy(local_bindings,proposed,sizeof(local_bindings));
}
static void local_command(const char *topic,const char *payload,int retained){
#ifdef WALLPANEL_DIRECT_MCU
    if(module_external||wp_lease_held())return;
#endif
    if(retained||dry_run||strlen(payload)!=3||payload[1]!=','||payload[0]<'0'||payload[0]>'2'||payload[2]<'0'||payload[2]>'2')return;
    for(int i=0;i<4;i++){
        char expected[128];snprintf(expected,sizeof(expected),BASE"/button/%d/binding/set",i+1);if(strcmp(topic,expected))continue;
        int a=payload[0]-'0',b=payload[2]-'0';
        if(local_bindings[i][0]!=a||local_bindings[i][1]!=b){
            int proposed[4][2];memcpy(proposed,local_bindings,sizeof(proposed));proposed[i][0]=a;proposed[i][1]=b;
            if(local_save(proposed)){puts("Local button binding save failed.");return;}
            memcpy(local_bindings,proposed,sizeof(local_bindings));gesture_cancel(i+1);
        }
        local_report=1;
    }
}
static int local_publish(void){
#ifdef WALLPANEL_DIRECT_MCU
    if(module_external)return 0;
#endif
    if(!local_report)return 0;
    for(int i=0;i<4;i++){
        char topic[128],payload[8];snprintf(topic,sizeof(topic),BASE"/button/%d/binding/state",i+1);
        snprintf(payload,sizeof(payload),"%d,%d",local_bindings[i][0],local_bindings[i][1]);if(publish(topic,payload,1))return -1;
    }
    if(publish(BASE"/knob/controller/capabilities","{\"frames\":true,\"local_buttons\":true}",1))return -1;
    if(publish(BASE"/module/capabilities","{\"buttons_enabled\":true,\"relays_enabled\":true}",1))return -1;
    local_report=0;return 0;
}
static void local_module_resumed(void){local_init();local_report=1;local_module_disconnected();}
