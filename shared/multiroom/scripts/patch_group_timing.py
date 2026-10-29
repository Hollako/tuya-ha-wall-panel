"""Reproducible, group-only changes to pinned Snapcast. No AirPlay sources."""
from pathlib import Path
root=Path(__file__).resolve().parents[1];source=root.parent/'multiroom-work/snapcast'
def patch(file,old,new):
 path=source/file;text=path.read_text()
 if new in text:return
 if text.count(old)!=1:raise RuntimeError('Unexpected pinned source: '+file)
 path.write_text(text.replace(old,new),encoding='utf-8')
patch('client/player/oboe_player.cpp','#include "common/str_compat.hpp"','#include "common/str_compat.hpp"\n#include "common/snap_exception.hpp"\n#include "group-output-timing.h"')
patch('client/player/oboe_player.cpp','    auto result = openStream();\n    LOG(INFO, LOG_TAG) << "BufferSizeInFrames:', '    auto result = openStream();\n    if (result != oboe::Result::OK || !out_stream_) throw SnapException("Cannot open group audio output");\n    LOG(INFO, LOG_TAG) << "BufferSizeInFrames:')
patch('client/player/oboe_player.cpp','    LOG(INFO, LOG_TAG) << "Hardware sample rate:', '    if (result != oboe::Result::OK || !out_stream_) return result;\n    LOG(INFO, LOG_TAG) << "Hardware sample rate:')
path=source/'client/player/oboe_player.cpp';text=path.read_text()
begin=text.index('double OboePlayer::getCurrentOutputLatencyMillis() const');end=text.index('\n\noboe::DataCallbackResult',begin)
replacement='''double OboePlayer::getCurrentOutputLatencyMillis() const
{
    static const int halMs = [] { const char* value = getenv("OUTPUT_LATENCY_MS"); return value ? std::max(0,std::min(500,atoi(value))) : 50; }();
    auto timestamp = out_stream_->getTimestamp(CLOCK_MONOTONIC);
    int64_t frame = -1, timeNs = 0;
    if (timestamp == oboe::Result::OK) {frame=timestamp.value().position;timeNs=timestamp.value().timestamp;}
    int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
    bool measured=false;
    int64_t delay = wallpanel_group::delayUs(out_stream_->getFramesWritten(),out_stream_->getFramesRead(),out_stream_->getSampleRate(),frame,timeNs,nowNs,halMs,&measured);
    // Rate-limited diagnostics distinguish hardware timestamps from the queue/HAL fallback.
    static int64_t lastLog=0;
    if(nowNs-lastLog>30000000000LL){lastLog=nowNs;LOG(INFO, LOG_TAG) << "Output delay [ms]: " << delay/1000.0 << ", timing: " << (measured?"timestamp":"queue+HAL estimate") << "\\n";}
    return delay/1000.0;
}'''
text=text[:begin]+replacement+text[end:];path.write_text(text,encoding='utf-8')
patch('client/client_connection.cpp','    socket_.connect(endpoint, ec);','    socket_.connect(endpoint, ec);\n    if (!ec) { boost::system::error_code ignored; socket_.set_option(boost::asio::ip::tcp::no_delay(true), ignored); }')
patch('server/stream_session_tcp.cpp','void StreamSessionTcp::start()\n{\n    readNext();','void StreamSessionTcp::start()\n{\n    boost::system::error_code ignored;\n    socket_.set_option(tcp::no_delay(true), ignored);\n    readNext();')
print('Group timestamp output, open-error guards and TCP_NODELAY patches applied.')
patch('client/controller.hpp','#include "stream.hpp"','#include "stream.hpp"\n#include "group-timeout-policy.h"')
patch('client/controller.hpp','    ClientSettings settings_;','    ClientSettings settings_;\n    wallpanel_group::TimeoutBudget timeSyncTimeouts_;\n    bool reconnecting_{false};\n    uint64_t connectionGeneration_{0};')
patch('client/controller.cpp','void Controller::reconnect()\n{','void Controller::reconnect()\n{\n    if (reconnecting_) return;\n    reconnecting_=true; ++connectionGeneration_; timeSyncTimeouts_.reset();')
patch('client/controller.cpp','void Controller::worker()\n{','void Controller::worker()\n{\n    reconnecting_=false;')
patch('client/controller.cpp','clientConnection_->getNextMessage([this](const boost::system::error_code& ec, std::unique_ptr<msg::BaseMessage> response)\n    {','clientConnection_->getNextMessage([this, generation=connectionGeneration_](const boost::system::error_code& ec, std::unique_ptr<msg::BaseMessage> response)\n    {\n        if (generation != connectionGeneration_) return;')
patch('client/controller.cpp','void Controller::sendTimeSyncMessage(int quick_syncs)\n{','''void Controller::sendTimeSyncMessage(int quick_syncs)
{
    int latency=wallpanel_group::roomLatency(getenv("GROUP_ROOM_DELAY_FILE"),settings_.player.latency);
    if(latency!=settings_.player.latency){
        settings_.player.latency=latency;
        if(stream_&&serverSettings_)stream_->setBufferLen(std::max(0,serverSettings_->getBufferMs()-serverSettings_->getLatency()-latency));
        LOG(INFO,LOG_TAG) << "Room timing changed [ms]: " << -latency << "\\n";
    }''')
patch('client/controller.cpp','[this, quick_syncs](const boost::system::error_code& ec, const std::unique_ptr<msg::Time>& response) mutable\n    {','[this, quick_syncs, generation=connectionGeneration_](const boost::system::error_code& ec, const std::unique_ptr<msg::Time>& response) mutable\n    {\n        if (generation != connectionGeneration_) return;')
patch('client/controller.cpp','''            LOG(ERROR, LOG_TAG) << "Time sync request failed: " << ec.message() << "\\n";
            reconnect();''','''            if(ec==boost::asio::error::timed_out && timeSyncTimeouts_.retry()){
                LOG(WARNING,LOG_TAG) << "Clock request timed out; keeping playback and retrying (" << timeSyncTimeouts_.failures << "/3)\\n";
                timer_.expires_after(1s);
                timer_.async_wait([this,quick_syncs,generation](const boost::system::error_code& cancelled){if(!cancelled&&generation==connectionGeneration_)sendTimeSyncMessage(quick_syncs);});
                return;
            }
            LOG(ERROR, LOG_TAG) << "Time sync request failed: " << ec.message() << "\\n";
            reconnect();''')
patch('client/controller.cpp','            TimeProvider::getInstance().setDiff(response->latency, response->received - response->sent);','            timeSyncTimeouts_.reset();\n            TimeProvider::getInstance().setDiff(response->latency, response->received - response->sent);')
print('Group bounded clock retries, reconnect generation guards and live room timing applied.')
patch('client/controller.hpp','#include "group-timeout-policy.h"','#include "group-timeout-policy.h"\n#include "group-volume.h"')
patch('client/controller.hpp','    boost::asio::steady_timer timer_;','    boost::asio::steady_timer timer_;\n    boost::asio::steady_timer volumeTimer_;\n    void refreshLocalVolume();')
patch('client/controller.cpp','timer_(io_context), settings_(settings)','timer_(io_context), volumeTimer_(io_context), settings_(settings)')
patch('client/controller.cpp','void Controller::start()\n{','''void Controller::refreshLocalVolume()
{
    auto old=wallpanel_group::localVolume.load(std::memory_order_relaxed);
    wallpanel_group::localVolume.store(wallpanel_group::readVolume(getenv("GROUP_VOLUME_FILE"),old),std::memory_order_relaxed);
    volumeTimer_.expires_after(20ms);
    volumeTimer_.async_wait([this](const boost::system::error_code& stopped){if(!stopped)refreshLocalVolume();});
}

void Controller::start()
{
    refreshLocalVolume();''')
patch('client/player/oboe_player.hpp','    std::vector<char> audio_data_;','    std::vector<char> audio_data_;\n    float groupVolume_{0.0f};')
patch('client/player/oboe_player.cpp','#include "group-output-timing.h"','#include "group-output-timing.h"\n#include "group-volume.h"')
patch('client/player/oboe_player.cpp','    return oboe::DataCallbackResult::Continue;','    if(stream_->getFormat().bits()==16)wallpanel_group::applyVolume(audioData,numFrames,stream_->getFormat().channels(),stream_->getFormat().rate(),groupVolume_);\n    return oboe::DataCallbackResult::Continue;')
print('Independent group software attenuation and smooth gain changes applied.')
