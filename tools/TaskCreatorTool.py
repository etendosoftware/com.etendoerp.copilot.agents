import os
import zipfile
import csv
import pandas as pd
import tempfile
import requests
from typing import Dict, Type

from copilot.core.etendo_utils import get_etendo_token, get_etendo_host
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_input import ToolInput, ToolField
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.exceptions import ToolException
from copilot.core.utils import read_optional_env_var

class TaskCreatorToolInput(ToolInput):
    file_path: str = ToolField(
        title="File Path",
        description="Path to the ZIP, CSV, XLS, or XLSX file."
    )
    task_type_id: str = ToolField(
        title="Task Type",
        description="ID of the task type."
    )
    status_id: str = ToolField(
        title="Status",
        description="ID of the pending status."
    )

class TaskCreatorTool(ToolWrapper):
    name: str = "TaskCreatorTool"  # SearchKey must match the class name.
    description: str = (
        "Processes a ZIP, CSV, or Excel file and creates a task for each extracted file or row. "
        "For ZIP files, each uncompressed file's full path is added to the description. "
        "For CSV or Excel files, the header row is skipped and each subsequent row task includes "
        "the header and the row data. If the file format is unsupported, a task is created with the file path."
    )
    args_schema: Type[ToolInput] = TaskCreatorToolInput

    TASK_TYPE: str = ""
    STATUS: str = ""
    ASSIGNED_USER: str = ""

    def send_taskapi_request(self, description: str) -> Dict:
        agent: str = ThreadContext.get_data("assistant_id")
        payload = {
            "taskType": self.TASK_TYPE,
            "status": self.STATUS,
            "assignedUser": self.ASSIGNED_USER,
            "etcopagDescription": description,
            "etcopagAgent": agent
        }
        access_token = get_etendo_token()
        etendo_host = read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")
        url = f"{etendo_host}/sws/com.etendoerp.etendorx.datasource/Task"
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {access_token}"
        }
        try:
            response = requests.post(url, headers=headers, json=payload)
            if response.status_code != 200:
                raise ToolException(f"Task creation failed with status {response.status_code}: {response.text}")
            return response.json()
        except Exception as e:
            raise ToolException(f"Error while sending API request: {str(e)}")

    def process_zip(self, zip_path: str):
        try:
            with tempfile.TemporaryDirectory() as tmp_dir:
                with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                    # Filter out Mac-specific files/folders.
                    members = [m for m in zip_ref.infolist() if not m.filename.startswith("__MACOSX/")]
                    zip_ref.extractall(tmp_dir, members=[m.filename for m in members])
                # Walk through the temporary directory to create tasks for each valid file.
                for root, _, files in os.walk(tmp_dir):
                    for file_name in files:
                        full_path = os.path.join(root, file_name)
                        description = f"Uncompressed file from ZIP: {full_path}"
                        self.send_taskapi_request(description)
        except Exception as e:
            raise ToolException(f"Error processing ZIP file {zip_path}: {str(e)}")

    def process_csv(self, csv_path: str):
        try:
            with open(csv_path, newline='', encoding='utf-8') as csvfile:
                reader = csv.reader(csvfile)
                headers = next(reader)  # Get header row.
                for row_number, row in enumerate(reader, start=2):
                    row_data = dict(zip(headers, row))
                    description = (
                        f"CSV File: {csv_path} - Row {row_number}:\n"
                        f"Headers: {headers}\nData: {row_data}"
                    )
                    self.send_taskapi_request(description)
        except Exception as e:
            raise ToolException(f"Error processing CSV file {csv_path}: {str(e)}")

    def process_xls(self, xls_path: str):
        try:
            df = pd.read_excel(xls_path)
            headers = list(df.columns)
            for index, row in df.iterrows():
                description = (
                    f"Excel File: {xls_path} - Row {index + 2}:\n"
                    f"Headers: {headers}\nData: {row.to_dict()}"
                )
                self.send_taskapi_request(description)
        except Exception as e:
            raise ToolException(f"Error processing Excel file {xls_path}: {str(e)}")

    def process_file(self, file_path: str):
        if not os.path.exists(file_path):
            raise ToolException(f"File not found: {file_path}")
        if file_path.endswith('.zip'):
            self.process_zip(file_path)
        elif file_path.endswith('.csv'):
            self.process_csv(file_path)
        elif file_path.endswith(('.xls', '.xlsx')):
            self.process_xls(file_path)
        else:
            description = f"Unsupported file format. File path: {file_path}"
            self.send_taskapi_request(description)

    def run(self, input_params: Dict, *args, **kwargs) -> Dict:
        # Retrieve dynamic task type, status, and assigned user.
        task_type = input_params.get("task_type_id")
        status = input_params.get("status_id")
        extra_info = ThreadContext.get_data("extra_info")
        assigned_user = extra_info.get("current_user_id")
        if not task_type or not status or not assigned_user:
            raise ToolException("task_type_id, status_id, and assigned_user must be provided and non-empty.")
        self.TASK_TYPE = task_type
        self.STATUS = status
        self.ASSIGNED_USER = assigned_user

        file_path = input_params.get("file_path")
        self.process_file(file_path)
        return {"message": "Task creation process completed."}
