from flask import request, jsonify
from HTTP_api.thread_manager import start_thread, stop_thread

def setup_routes(app):
    @app.route('/start_stream', methods=['POST'])
    def start_stream():
        data = request.get_json()
        rtsp_urls = data.get('rtsp_urls')
        model_paths = data.get('model_paths')
        taskId = data.get('task_id')

        if not rtsp_urls or not model_paths:
            return jsonify({"error": "rtsp_urls和model_paths是必需的"}), 400

        name = start_thread(rtsp_urls, model_paths,taskId)
        return jsonify({"thread_name": name})

    @app.route('/stop_stream/', methods=['POST'])
    def stop_stream():
        data = request.get_json()
        name = data.get('name')
        result = stop_thread(name)
        if result:
            return jsonify({"status": "已停止"}), 200
        else:
            return jsonify({"error": "线程未找到或未运行"}), 404